# 인프라 — AWS 프리 티어 EC2 + DuckDNS + Caddy (전부 무료)

> 제출 요건: 심사 중 상시 접속 가능해야 하고, 웹은 **HTTPS 필수**.
> 구성: EC2 한 대에 [Caddy:443(HTTPS)] → [Spring:8080] → [FastAPI AI:8000] + PostgreSQL.

## 사전 준비 (사람이 하는 일, ~20분)

1. **AWS 계정** (신규면 12개월 프리 티어)
2. **EC2 인스턴스 생성**
   - AMI: Ubuntu 24.04 LTS · 타입: **t3.micro 또는 t2.micro** (프리 티어 표시 확인)
   - 키 페어 새로 생성 → `.pem` 다운로드 (분실 시 재접속 불가)
   - 보안 그룹 인바운드: **22(내 IP), 80, 443(전체)** — 8080/8000은 열지 않음(내부 전용)
3. **DuckDNS** (https://www.duckdns.org, GitHub 로그인)
   - 서브도메인 생성 (예: `mildang`) → EC2 퍼블릭 IP 입력 → 페이지 상단 token 복사
4. AI팀에게 `OPENAI_API_KEY` / `OPENAI_MODEL` 받기

## 셋업 (1회)

```bash
# 로컬에서 접속
ssh -i key.pem ubuntu@<EC2-IP>

# 스크립트 받기 + 변수 채우기 + 실행
curl -O https://raw.githubusercontent.com/inha-likelion-team2/mildang-server/main/infra/setup-ec2.sh
nano setup-ec2.sh        # 상단 변수 6개 입력 (DOMAIN·DUCKDNS_TOKEN·DB_PASSWORD·JWT_SECRET·OPENAI_*)
sudo bash setup-ec2.sh
```

## 배포 (백엔드 코드 바뀔 때마다)

```powershell
# 로컬 mildang-server 폴더에서
.\infra\deploy-backend.ps1 -EC2Host <EC2-IP> -PemPath C:\path\to\key.pem
```

확인: `https://<도메인>/v1/health` → `{"status":"ok"}`

## AI 서버 갱신

```bash
ssh -i key.pem ubuntu@<EC2-IP>
cd /opt/mildang-ai && sudo git pull && sudo systemctl restart mildang-ai
```

## 문제 해결

| 증상 | 확인 |
|------|------|
| 서비스 상태 | `systemctl status mildang-server mildang-ai caddy` |
| 백엔드 로그 | `journalctl -u mildang-server -f` |
| HTTPS 인증서 실패 | DuckDNS IP가 EC2 IP와 같은지 + 보안그룹 80/443 오픈 확인 (Caddy가 발급에 80 사용) |
| 메모리 부족(OOM) | `free -h`로 swap 확인. jar 빌드를 서버에서 하지 말 것 |

## 비용 안전장치

- AWS 콘솔 → Billing → **Budgets에서 $1 예산 알림** 생성 권장
- 프리 티어는 t2/t3.micro **1대**만 무료 — 인스턴스 추가 생성 금지
