# mildang-server

밀당 — 밀가루 흥정 챌린지 앱의 백엔드 API 서버.

> **기준 문서: API 명세서 v1.3 · DB 스키마 v3.0** (2026-08-13 반영 완료)

## 스택

- Java 21 · Spring Boot 4.1 (Web MVC, Validation, Data JPA)
- DB: H2(local) / PostgreSQL(demo·prod)
- 인증: JWT (jjwt) · ID: ULID (`chl_01H...` 형식)
- AI: 같은 EC2의 FastAPI AI-Server(`localhost:8000`)를 내부 HTTP로 호출

## 실행

```bash
./gradlew bootRun          # local 프로필(H2 인메모리)로 기동 — AI는 기본 Fake
./gradlew test             # 테스트
```

기동 후 확인: `GET http://localhost:8080/v1/health` → `{"status":"ok"}`

## 브라우저 테스트 (팀원용 — AI 서버 없이 바로 됩니다)

서버를 켠 뒤 브라우저에서 아래 중 하나로 접속하세요. 둘 다 임시물이며 실 프론트 완성 시 삭제됩니다.

| URL | 용도 |
|-----|------|
| `http://localhost:8080/v1/app/index.html` | **데모 프론트** — 와이어프레임 그대로 재현한 화면. 온보딩→대시보드→식사→흥정→스캔→체크인→리포트 전 플로우 |
| `http://localhost:8080/v1/tester/index.html` | **QA 테스터** — 요청/응답 JSON 로그가 그대로 보이는 개발자용 |

사용법 (`/app` 기준):

1. **카카오로 시작** — 아무 유저로 로그인돼 새 챌린지 온보딩부터 시작
2. 우상단 **⚙(데모 도구)** — 심사위원 계정(judge-02/03/05), 시드 7종, **+1일/+7일**, **배치**(05:00 배치 즉시 실행), 리셋
3. 완주 시나리오: 온보딩 완료 → 식사 기록 → ⚙에서 **+7일 → 배치** → 리포트 자동 진입 (또는 시드 «완주» 버튼)
4. 스캔은 아무 메뉴판 사진(JPEG/PNG)이나 올리면 됩니다 — Fake AI는 항상 김밥천국 5메뉴를 반환
5. 기본은 Fake AI (라면·김밥·빵·떡볶이·치킨·칼국수·삼겹살·된장찌개·제육볶음·냉면 10종만 인식, 그 외는 후보 3개 제안 플로우). 자유 메뉴 인식은 실 AI 연동 필요 (아래)

이상한 응답·오류·어색한 문구를 발견하면 편하게 알려주세요 — 그게 이 테스터의 목적입니다.

**실제 AI-Server와 연동하려면**

```bash
# AI-Server (Mildang-AI-Server repo)
uvicorn app.main:app --port 8000

# 백엔드
AI_FAKE=false ./gradlew bootRun
```

`AI_FAKE`가 true(local 기본)면 `FakeAiGateway`가 결정적 응답(라면 80 등)을 주므로 AI 서버 없이 개발할 수 있습니다.

> Windows에서 프로젝트 경로에 한글이 있으면 Gradle 테스트가 깨집니다. 영문 경로에 클론하세요.

## 프로필

| 프로필 | 용도 | 비고 |
|--------|------|------|
| `local` | 로컬 개발 (기본) | H2 인메모리, `/demo/*` 활성, AI Fake 기본 |
| `demo` | 공모전 제출 빌드 | 외부 연동 목 처리, `/demo/*` 활성, 목 응답에 `"mocked": true` |
| `prod` | 실서비스 | `/demo/*` 404, 목 전면 비활성 |

환경변수: `DB_URL` `DB_USERNAME` `DB_PASSWORD` `JWT_SECRET` `AI_BASE_URL`(기본 `http://localhost:8000`) `AI_FAKE`

## 구조

```
app.mildang
├── common/        에러 봉투(ErrorCode·ApiException), LogicalDate(05:00 KST 일자 경계), Ids(ULID), 인증(JWT)
├── auth · user    카카오 목 로그인(§14.3), 기기=세션(user_sessions, 토큰 SHA-256 회전)
├── challenge      플랜·챌린지·예산(BudgetPolicy: 주간×곱수 총액)·대시보드(current)
├── item           항목(3a·3b 공용) — record/prepay 멱등(§6.9), 잔액 스냅샷
├── analysis       3c 텍스트 분석 — AI 검증 게이트 + 재시도 + 422 후보 3개
├── ai             AiGateway(HttpAiGateway ↔ FakeAiGateway)
├── haggle         5 밀당 대화 — frame 고정, 턴 서버 진실(최대 10), close는 잔액 불변
├── scan           4a·4b 메뉴판 스캔 — 전처리→추출→일괄 추정→싼 순·추천·코멘트, 실측가 PATCH
├── tip · report   대시보드 팁(일 1회) · 완주 리포트(발견·하이라이트·재대결 CTA)·공유·초대
├── checkin        컨디션 체크인 (멱등 PUT, checkinDays)
├── payment        결제 목 (MOCK 항상 PAID, receipt 멱등)
├── batch          05:00 KST 배치 3종 (만료·선차감 전환·챌린지 정리)
├── demo           /demo/* — seed 7종·reset·advance-day·run-batch (prod 404)
└── (static)       /v1/app·/v1/tester — 임시 테스트 프론트 (실 FE 완성 시 삭제)
```

## 핵심 규칙 (API 명세 v1.3)

- **예산은 기간 총액 하나** — `budget.total = 주간값 × 곱수(W1×1·W2×2·W4×4)`, 잔액도 `budget.balance` 하나. 주차·구간 잔액 없음 (§0.10-11)
- 항등식 `balance = total − spent − prepaid` 상시 성립. `PREPAID→RECORDED`는 prepaid→spent **이동**(잔액 불변)
- 잔액 변경은 `record`·`prepay` 두 곳에서만 — 흥정 종료는 잔액을 건드리지 않는다
- **멱등(§6.9)**: 같은 종착 상태 재요청 = 200 + `alreadyProcessed:true`, 교차 전이 = 409
- 하루의 경계는 매일 **05:00 KST** (자정 아님) — 배치 3종도 전부 05:00
- 포인트는 정수 0~999, 잔액만 음수 허용 — **초과는 항상 허용**
- AI 반환값은 백엔드 검증 게이트(§15.8.2) 통과분만 응답에 실림 — 재시도 1회 → 폴백

## 심사용 시드 (idToken으로 로그인)

`demo-judge-01`(신규) · `02`(W1 4일차, 52/13/20) · `03`(완주·리포트) · `04`(W4 12일차, 280/340) · `05`(W2 8일차, 90/170)
`POST /demo/seed {scenario}` — `FRESH · DAY4_ACTIVE · COMPLETED · W2_DAY8 · W4_DAY12 · LOW_BALANCE · EXPIRED_CONFIRM`
`POST /demo/run-batch {jobs}` — `PREPAID_CONVERT · ITEM_EXPIRY · CHALLENGE_CLOSE` (05:00 배치 즉시 실행)
