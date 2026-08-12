# mildang-server

밀당 — 밀가루 흥정 챌린지 앱의 백엔드 API 서버.

## 스택

- Java 21 · Spring Boot 4.1 (Web MVC, Validation, Data JPA)
- DB: H2(local) / PostgreSQL(demo·prod)
- 인증: JWT (jjwt) · ID: ULID (`chl_01H...` 형식)

## 실행

```bash
./gradlew bootRun          # local 프로필(H2 인메모리)로 기동
./gradlew test             # 테스트
```

기동 후 확인: `GET http://localhost:8080/v1/health` → `{"status":"ok"}`

> Windows에서 프로젝트 경로에 한글이 있으면 Gradle 테스트가 깨집니다. 영문 경로에 클론하세요.

## 프로필

| 프로필 | 용도 | 비고 |
|--------|------|------|
| `local` | 로컬 개발 (기본) | H2 인메모리, `/demo/*` 활성 |
| `demo` | 공모전 제출 빌드 | 외부 연동 목 처리, `/demo/*` 활성, 목 응답에 `"mocked": true` |
| `prod` | 실서비스 | `/demo/*` 404, 목 전면 비활성 |

DB 접속 정보는 환경변수로 주입: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`. AI 서버 주소는 `AI_BASE_URL`(기본 `http://localhost:8000`).

## 구조

```
app.mildang
├── common/        에러 봉투(ErrorCode·ApiException), LogicalDate(05:00 KST 일자 경계), Ids(ULID)
├── demo/          데모 전용 라우트 — @Profile({"local","demo"}), prod에서 404
└── (auth · challenge · payment · item · haggle · analysis · checkin · report)  ← 도메인별 추가 예정
```

## 핵심 규칙 (API 명세 v1.1)

- 성공 응답은 봉투 없음, 실패는 `{"error": {code, message, field?, detail?}}`
- 잔액 변경은 `record`·`prepay` 두 곳에서만 — 흥정 종료는 잔액을 건드리지 않는다
- 하루의 경계는 매일 **05:00 KST** (자정 아님) — `LogicalDate` 유틸만 사용
- 포인트는 정수 0~999, 잔액만 음수 허용 — 초과는 항상 허용된다
