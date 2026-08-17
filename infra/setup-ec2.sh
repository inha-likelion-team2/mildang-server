#!/usr/bin/env bash
# =====================================================================
# 밀당 EC2 초기 셋업 — Ubuntu 22.04/24.04, t2/t3.micro(프리 티어) 기준
# 1회 실행: sudo bash setup-ec2.sh
# 실행 전 아래 변수 4개를 채울 것.
# =====================================================================
set -euo pipefail

# ---------- 채워야 하는 값 ----------
DOMAIN="mildang.duckdns.org"        # DuckDNS에서 만든 서브도메인
DUCKDNS_TOKEN="여기에-duckdns-토큰"   # duckdns.org 로그인하면 보이는 token
DB_PASSWORD="여기에-DB-비밀번호"       # 아무 랜덤 문자열
JWT_SECRET="여기에-32바이트-이상-시크릿" # 32자 이상 랜덤 문자열
OPENAI_API_KEY="여기에-키"            # AI팀에게 받기 (AI 서버용)
OPENAI_MODEL="여기에-모델명"           # AI팀이 쓰는 모델
KAKAO_APP_KEY=""                     # 카카오 앱의 REST API 키. prod 프로필로 올릴 때만 필요
                                     # (아래 서비스는 demo 프로필이라 카카오 검증을 하지 않는다)
# ------------------------------------

echo "== 1. swap 2GB (t2.micro RAM 1GB 보강) =="
if ! swapon --show | grep -q swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "== 2. 패키지 설치 (JDK 21 · Python · PostgreSQL · Caddy) =="
apt-get update -y
apt-get install -y openjdk-21-jre-headless python3-venv python3-pip postgresql git curl
# Caddy 공식 저장소
apt-get install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list
apt-get update -y && apt-get install -y caddy

echo "== 3. PostgreSQL — mildang DB/계정 =="
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='mildang'" | grep -q 1 || \
  sudo -u postgres psql -c "CREATE USER mildang WITH PASSWORD '${DB_PASSWORD}';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='mildang'" | grep -q 1 || \
  sudo -u postgres psql -c "CREATE DATABASE mildang OWNER mildang;"

echo "== 4. DuckDNS IP 갱신 (5분마다) =="
mkdir -p /opt/duckdns
cat > /opt/duckdns/update.sh <<EOF
#!/usr/bin/env bash
curl -s "https://www.duckdns.org/update?domains=${DOMAIN%%.duckdns.org}&token=${DUCKDNS_TOKEN}&ip=" > /opt/duckdns/last.log
EOF
chmod +x /opt/duckdns/update.sh
/opt/duckdns/update.sh
(crontab -l 2>/dev/null | grep -v duckdns; echo "*/5 * * * * /opt/duckdns/update.sh") | crontab -

echo "== 5. AI-Server 클론 + venv =="
if [ ! -d /opt/mildang-ai ]; then
  git clone -b ai-server-integration https://github.com/inha-likelion-team2/Mildang-AI-Server /opt/mildang-ai
fi
python3 -m venv /opt/mildang-ai/.venv
/opt/mildang-ai/.venv/bin/pip install -r /opt/mildang-ai/requirements.txt
cat > /opt/mildang-ai/.env <<EOF
OPENAI_API_KEY=${OPENAI_API_KEY}
OPENAI_MODEL=${OPENAI_MODEL}
EOF
chmod 600 /opt/mildang-ai/.env

echo "== 6. systemd 서비스 등록 =="
mkdir -p /opt/mildang
cat > /etc/systemd/system/mildang-ai.service <<'EOF'
[Unit]
Description=Mildang AI Server (FastAPI)
After=network.target

[Service]
WorkingDirectory=/opt/mildang-ai
ExecStart=/opt/mildang-ai/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8000
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

cat > /etc/systemd/system/mildang-server.service <<EOF
[Unit]
Description=Mildang Backend (Spring Boot)
After=network.target postgresql.service mildang-ai.service

[Service]
WorkingDirectory=/opt/mildang
Environment=SPRING_PROFILES_ACTIVE=demo
Environment=DB_URL=jdbc:postgresql://localhost:5432/mildang
Environment=DB_USERNAME=mildang
Environment=DB_PASSWORD=${DB_PASSWORD}
Environment=JWT_SECRET=${JWT_SECRET}
Environment=AI_BASE_URL=http://localhost:8000
Environment=AI_FAKE=false
Environment=PUBLIC_BASE_URL=https://${DOMAIN}
# ⚠ 지금은 demo 프로필이라 idToken을 검증하지 않는다(아무 문자열이나 계정이 된다).
#    실서비스로 올릴 땐 SPRING_PROFILES_ACTIVE=prod 로 바꾸고 아래를 함께 켤 것.
# Environment=KAKAO_APP_KEY=${KAKAO_APP_KEY}
ExecStart=/usr/bin/java -Xmx350m -jar /opt/mildang/mildang-server.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

echo "== 7. Caddy — 자동 HTTPS 리버스 프록시 + 접근 제한 =="
# ⚠ 우리는 demo 프로필로 배포한다 = idToken 문자열이 곧 계정 키다.
# 주소만 알면 누구나 심사위원 계정 토큰을 받고 /demo/reset 으로 데이터를 지울 수 있으므로
# 사이트 전체를 basic auth 뒤에 둔다. 심사위원에게 아이디/비번을 함께 전달할 것.
# 해시 생성: caddy hash-password --plaintext '원하는비번'
SITE_USER="${SITE_USER:-judge}"
SITE_PASSWORD_HASH="${SITE_PASSWORD_HASH:-}"
if [ -z "${SITE_PASSWORD_HASH}" ]; then
  echo "!! SITE_PASSWORD_HASH가 비어 있습니다."
  echo "   caddy hash-password --plaintext '비번' 으로 해시를 만든 뒤 환경변수로 넣고 다시 실행하세요."
  echo "   예: SITE_USER=judge SITE_PASSWORD_HASH='\$2a\$14\$...' sudo -E bash setup-ec2.sh"
  exit 1
fi

cat > /etc/caddy/Caddyfile <<EOF
${DOMAIN} {
    # 헬스체크만 열어둔다 — 배포 스크립트와 외부 모니터링이 인증 없이 확인해야 한다
    @health path /v1/health
    handle @health {
        reverse_proxy localhost:8080
    }

    handle {
        basic_auth {
            ${SITE_USER} ${SITE_PASSWORD_HASH}
        }
        reverse_proxy localhost:8080
    }
}
EOF

systemctl daemon-reload
systemctl enable --now mildang-ai
systemctl enable postgresql caddy mildang-server
systemctl restart caddy

echo ""
echo "== 완료 =="
echo "다음 단계: 로컬에서 infra/deploy-backend.ps1 로 jar를 올리면 서버가 뜹니다."
echo "확인: https://${DOMAIN}/v1/health   (헬스체크만 인증 없이 열려 있습니다)"
echo ""
echo "!! 앱 접속에는 basic auth가 걸려 있습니다 — 아이디 '${SITE_USER}' + 설정한 비번."
echo "!! 심사위원에게 주소와 함께 이 계정을 전달하세요."
echo "!! 이 서버는 demo 프로필로 돕니다 (카카오 검증 없음·목 결제·/demo/* 활성)."
