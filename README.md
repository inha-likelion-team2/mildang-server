# mildang-server

공모전 팀 프로젝트 "밀당"(밀가루 흥정 챌린지 앱)의 백엔드 API 서버입니다. 백엔드를 맡아 커밋 93개 중 90개를 작성했습니다.

| | |
|---|---|
| 기술 | Java 21 · Spring Boot · JPA · PostgreSQL · JWT · Docker |
| 연동 | 토스페이먼츠 결제 · 카카오 로그인 · AI 서버(FastAPI, 내부 HTTP) |
| 프론트엔드 | [killerwhale-15/mildang](https://github.com/killerwhale-15/mildang) |

## 설계에서 신경 쓴 것

- **결제 중복 승인 방지** — 같은 주문번호로 다시 요청하면(뒤로가기·더블탭) 승인 API를 다시 부르지 않고 기존 결제를 돌려줍니다
- **잔액 정합성** — `잔액 = 총액 − 사용 − 선결제`가 항상 성립하도록 잔액을 바꾸는 곳을 두 군데로 제한했습니다
- **멱등한 상태 전이** — 같은 종착 상태 재요청은 200, 다른 상태로 넘어가려는 요청은 409로 응답합니다
- **외부 AI 장애 격리** — 트랜잭션 안에서 부르는 AI 서버에 타임아웃을 걸어, AI가 멈춰도 DB 커넥션 풀이 마르지 않습니다

## 실행

```bash
./gradlew bootRun   # local 프로필(H2 인메모리) · AI는 Fake 응답
./gradlew test
```

`GET http://localhost:8080/v1/health` → `{"status":"ok"}`

## 문서

- [API 명세서](docs/API-명세서.md)
- [개발 가이드](docs/개발-가이드.md) — 브라우저 테스트, 프로필·환경변수, 패키지 구조, 심사용 시드
- [배포(Railway)](docs/배포-Railway.md)
