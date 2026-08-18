# 밀당 백엔드 — Railway 배포

> 프론트는 **Vercel**, 백엔드는 **Railway**. 가비아는 설정이 복잡해 쓰지 않습니다 (팀 결정 2026-08-18).
> 이 문서대로 하면 **처음 배포까지 15분** 정도입니다.

레포에 이미 들어 있는 것 — `Dockerfile` · `.dockerignore` · `$PORT` 바인딩 · Railway Postgres 변수 자동 인식.
**따로 코드를 고칠 필요는 없습니다.**

---

## 1. 왜 demo 프로필로 올리나

| 프로필 | `ddl-auto` | `/demo/*` | 카카오 검증 |
| --- | --- | --- | --- |
| `demo` | `update` — **테이블 자동 생성** | 열림 | `/auth/social`은 통과, `/auth/kakao`는 실검증 |
| `prod` | `validate` — **테이블이 없으면 기동 실패** | 닫힘 | 전부 실검증 |

**심사 시연에는 `demo`가 맞습니다.** 심사위원 원탭 로그인(`judge-01`)과 시드 버튼이 `/demo/*`에 있고, 빈 DB에 테이블을 손으로 만들지 않아도 됩니다.
카카오 실로그인은 `demo`에서도 **그대로 동작합니다** — `/auth/kakao`는 프로필과 무관하게 항상 진짜로 검증합니다.

> 실서비스로 전환할 때 `SPRING_PROFILES_ACTIVE=prod`로 바꾸고, 그전에 `docs/DB-변경-v3.1.md`의 DDL을 적용하세요.

---

## 2. Railway 설정 (클릭 순서)

**1) 프로젝트 만들기** — [railway.app](https://railway.app) → **New Project** → **Deploy from GitHub repo** → `inha-likelion-team2/mildang-server` 선택.

Railway가 `Dockerfile`을 자동으로 찾아 씁니다. 빌드 설정은 건드릴 게 없습니다.

**2) Postgres 붙이기** — 같은 프로젝트에서 **New → Database → Add PostgreSQL**.

붙이면 `PGHOST` `PGPORT` `PGDATABASE` `PGUSER` `PGPASSWORD`가 자동으로 들어옵니다.
**`DB_URL`을 따로 넣지 않아도 붙습니다** — 앱이 이 변수들을 그대로 읽게 해 뒀습니다.

**3) 환경변수 넣기** — 서비스 → **Variables**.

| 변수 | 값 | 필수 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `demo` | ✅ |
| `JWT_SECRET` | 32자 이상 랜덤 문자열 | ✅ |
| `PUBLIC_BASE_URL` | `https://<서비스>.up.railway.app/v1` | ✅ |
| `AI_BASE_URL` | AI 서버 주소 | AI 붙일 때 |
| `AI_FAKE` | `false` | AI 붙일 때 |
| `KAKAO_APP_KEY` | 카카오 REST API 키 | 카카오 로그인 |
| `KAKAO_REST_API_KEY` | 같은 값 | 카카오 로그인 |
| `KAKAO_CLIENT_SECRET` | 콘솔의 클라이언트 시크릿 | 카카오 로그인 |
| `TOSS_CLIENT_KEY` | `test_gck_…` | 결제 |
| `TOSS_SECRET_KEY` | `test_gsk_…` | 결제 |

⚠ **`PUBLIC_BASE_URL`에 `/v1`까지 붙여야 합니다.** 공유 카드의 딥링크(`{PUBLIC_BASE_URL}/c/{code}`)가 여기서 만들어집니다.

**4) 도메인 받기** — 서비스 → **Settings → Networking → Generate Domain**.

`https://xxx.up.railway.app`가 나옵니다. **HTTPS가 자동으로 붙습니다** — 인증서를 따로 만들 필요가 없고, 그래서 **카메라와 공유 시트(Web Share)도 그대로 동작**합니다.

**5) 확인**

```
https://<도메인>/v1/health          → 200 {"status":"UP"}
https://<도메인>/v1/app/index.html  → 데모 화면
```

---

## 3. 배포 후 반드시 할 것

**카카오 Redirect URI 추가** — `앱 → 플랫폼 키 → REST API 키 → 리다이렉트 URI`에 아래를 **추가**합니다(기존 것은 두세요):

```
https://<도메인>/v1/app/index.html
```

프론트가 Vercel에 올라가면 **그 주소도** 같은 자리에 추가해야 합니다.

**Vercel 프론트가 부를 주소**를 백엔드 도메인으로 잡아 주세요:

```
NEXT_PUBLIC_API_BASE=https://<도메인>/v1
```

CORS는 **이미 전 오리진 열려 있습니다**(`allowedOriginPatterns("*")`). Vercel 프리뷰 도메인이 배포마다 바뀌어도 걸리지 않습니다.
⚠ 실서비스 전에는 FE 도메인으로 좁히세요 — `WebConfig.addCorsMappings`.

---

## 4. 자주 막히는 곳

| 증상 | 원인 | 조치 |
| --- | --- | --- |
| 배포는 됐는데 502 | `$PORT`를 안 듣는 상태 | `Dockerfile`의 `--server.port=${PORT}` 확인. 직접 `server.port`를 다른 값으로 덮지 말 것 |
| 기동 중 DB 오류 | Postgres 플러그인 미연결 | 같은 **프로젝트 안에** 붙였는지 확인. 다른 프로젝트면 변수가 안 넘어옴 |
| `Table not found` | 프로필이 `prod` | `SPRING_PROFILES_ACTIVE=demo`인지 확인 |
| 카카오 로그인 401 | `KAKAO_CLIENT_SECRET` 누락 | 새 콘솔은 시크릿이 **기본 활성화**. 없으면 토큰 교환이 401 |
| 공유 카드 링크가 localhost | `PUBLIC_BASE_URL` 미설정 | 도메인 + `/v1`로 넣기 |
| 심사위원 로그인 안 됨 | 프로필이 `prod` | `demo`여야 `/demo/*`와 원탭 로그인이 열림 |

**로그는 Railway 서비스 → Deployments → 해당 배포 → Logs**에서 봅니다. 기동 시 이런 줄이 보이면 정상입니다:

```
The following 1 profile is active: "demo"
Tomcat started on port XXXX (http) with context path '/v1'
```

---

## 5. 비용

Railway 무료 크레딧으로 해커톤 기간은 충분합니다. Postgres + 앱 컨테이너 하나면 됩니다.
**슬립(휴면)이 없어서** 심사위원이 아무 때나 눌러도 바로 뜹니다 — 이게 무료 PaaS 중에 Railway를 고른 이유입니다.
