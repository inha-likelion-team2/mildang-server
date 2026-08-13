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
ExecStart=/usr/bin/java -Xmx350m -jar /opt/mildang/mildang-server.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

echo "== 7. Caddy — 자동 HTTPS 리버스 프록시 =="
cat > /etc/caddy/Caddyfile <<EOF
${DOMAIN} {
    reverse_proxy localhost:8080
}
EOF

systemctl daemon-reload
systemctl enable --now mildang-ai
systemctl enable postgresql caddy mildang-server
systemctl restart caddy

echo ""
echo "== 완료 =="
echo "다음 단계: 로컬에서 infra/deploy-backend.ps1 로 jar를 올리면 서버가 뜹니다."
echo "확인: https://${DOMAIN}/v1/health"
