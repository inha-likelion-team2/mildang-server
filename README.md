# mildang-server — 밀가루 흥정 챌린지 앱 "밀당"의 백엔드

공모전 팀 프로젝트 "밀당"의 API 서버입니다. 밀가루 음식을 끊는 대신 **'밀가루 예산(포인트)'으로 관리하고, AI 캐릭터 '밀당이'와 흥정하며 정해진 기간을 버티는** 챌린지 앱입니다. 슬로건은 "끊지 마세요. 밀당하세요."

| | |
|---|---|
| 형태 | 공모전 팀 프로젝트(트랙상 수상) · 기획 · 프론트엔드 · AI · 백엔드 |
| 기간 | 2026-08-12 ~ 2026-08-20 (백엔드 커밋 기준) |
| **내 역할** | **백엔드 전담** — 머지 커밋 제외 93개 중 91개 작성 |
| 규모 | 엔드포인트 34개 · 테스트 메서드 182개(테스트 파일 34개) · main Java 파일 110개 |
| 기술 | Java 21 · Spring Boot 4.1 · JPA · PostgreSQL(로컬 H2) · JWT · Docker · Railway |
| 연동 | AI 서버(FastAPI, AI 담당 팀원) · 토스페이먼츠 · 카카오 로그인 |
| 프론트엔드 | [killerwhale-15/mildang](https://github.com/killerwhale-15/mildang) (React 19 · Vite · PWA) |

## 앱이 하는 일

1. 설문으로 주간 예산을 정합니다(예: 기본 설문 기준 주 190). 기간은 1주·2주·4주이고 잔액은 하나입니다.
2. 먹고 싶은 메뉴를 글이나 메뉴판 사진으로 넣으면 AI가 포인트를 추정합니다.
3. 밀당이와 최대 10턴 흥정해 양을 줄이거나, 약속 요일을 잡아 미리 차감(선차감)합니다.
4. 체크인·체중을 기록하고, 기간이 끝나면 완주 리포트와 공유 카드를 받습니다.

"금지가 아니라 협상, 실패가 아니라 지출"이라서 예산 초과도 막지 않고 기록합니다.

## 구조

```
 프론트 (React PWA, Vercel)
        │  HTTPS · JSON · Bearer JWT
        ▼
 ┌──────────── mildang-server (Spring Boot, Railway) ────────────┐
 │ auth · challenge · item · haggle · scan · checkin · report    │
 │ payment · batch(05:00 KST 3종) · demo(/demo/*, prod에서 404)   │
 └───┬──────────────┬──────────────────┬───────────────┬─────────┘
     ▼              ▼                  ▼               ▼
 PostgreSQL   AI 서버(FastAPI)     토스페이먼츠      카카오 로그인
              추정·흥정·스캔·리포트  결제 승인         인가 코드 교환
              (로컬은 Fake AI)
```

- 하루의 경계는 자정이 아니라 **05:00 KST**입니다(`LogicalDate`). 배치 3종(만료·선차감 전환·챌린지 정리)도 05:00에 돕니다.
- 프로필은 `local`(H2·Fake AI) / `demo`(공모전 제출 빌드, 심사위원 시드 계정) / `prod`(`/demo/*` 닫힘)입니다.
- 패키지별 역할은 [개발 가이드 - 구조](docs/개발-가이드.md#구조)에 있습니다.

## 내가 한 일

**결제를 두 번 승인하지 않게** — 같은 주문번호 재요청(뒤로가기·더블탭)은 승인 API를 다시 부르지 않고 기존 결제를 200으로 돌려줍니다. 토스 승인 요청에는 주문번호를 `Idempotency-Key`로 실어 보냅니다. 금액은 클라이언트 값을 믿지 않고 요금제로 다시 계산해 대조합니다(안 그러면 브라우저에서 금액을 바꿔 4주권을 살 수 있습니다). → [`PaymentService.confirm`](src/main/java/app/mildang/payment/PaymentService.java), [커밋 0bb0dea](https://github.com/inha-likelion-team2/mildang-server/commit/0bb0dea)

**잔액 항등식을 테스트로 고정** — `잔액 = 총예산 − 기록분 − 선차감분`이 항상 성립해야 합니다. 잔액을 바꾸는 곳을 `record`·`prepay` 두 곳으로 제한하고, 생성·기록·멱등 재요청·선차감·선차감→기록 이동·초과·만료 소급까지 전이마다 항등식을 검사하는 `InvariantsTest`를 넣었습니다. → [커밋 91e0ca0](https://github.com/inha-likelion-team2/mildang-server/commit/91e0ca0)

**멱등한 상태 전이** — 같은 종착 상태 재요청은 200 + `alreadyProcessed: true`, 다른 상태로 넘어가려는 요청은 409입니다. 선차감 항목을 기록하면 잔액은 그대로 두고 선차감분에서 기록분으로 칸만 옮깁니다. → [`ItemService.record`·`prepay`](src/main/java/app/mildang/item/ItemService.java)

**중복 제출을 서버에서 병합** — 한글 입력기에서 Enter가 두 번 먹혀 같은 항목이 두 번 차감됐습니다. 프론트 가드만으로는 빠뜨리는 순간 재발하므로, 같은 내용의 확정 전 항목이 3초 안에 있으면 새로 만들지 않고 그 항목을 돌려줍니다. 멱등 키도 검토했지만 키를 "의도" 단위로 만들어야 해서 결국 프론트 규율에 기대게 돼 택하지 않았습니다. → [커밋 2f65fc9](https://github.com/inha-likelion-team2/mildang-server/commit/2f65fc9)

**AI가 멈춰도 API 전체가 멈추지 않게** — AI 호출이 트랜잭션 안이라, AI가 거절이 아니라 멈추면 DB 커넥션까지 붙잡고 풀(기본 10)이 마르면 API 전체가 멈춥니다. 연결 2초·응답 45초 타임아웃을 요청 팩토리에 직접 넣었습니다(정적 빌더는 `spring.http.client.*` 설정을 타지 않습니다). 응답 상한은 스캔 실측 21.5초를 보고 30초에서 늘렸습니다. → [`HttpAiGateway`](src/main/java/app/mildang/ai/HttpAiGateway.java), [커밋 c8bbde2](https://github.com/inha-likelion-team2/mildang-server/commit/c8bbde2), [5850423](https://github.com/inha-likelion-team2/mildang-server/commit/5850423)

**배포와 심사 준비** — Railway 배포(Dockerfile·`$PORT`·Postgres 자동 인식), `/health`가 DB를 실제로 확인하고 `codeVersion`으로 무엇이 배포됐는지 밖에서 보이게 했습니다. 심사위원이 7일을 기다리지 않도록 상태별 시드 계정과 날짜 넘기기·배치 즉시 실행 도구를 만들었습니다.

## 트러블슈팅

| 문제 | 원인 | 해결 |
|---|---|---|
| 다른 오리진의 프론트가 보호된 경로를 아예 못 부름(500) | 브라우저는 preflight(OPTIONS)에 `Authorization`을 싣지 않는데 인증 인터셉터가 토큰을 요구했습니다. preflight 핸들러는 컨트롤러가 아니라서 `@RestControllerAdvice`가 예외를 못 잡고 401 대신 500이 나가, CORS 설정을 의심하기 쉬웠습니다 | `CorsUtils.isPreFlightRequest`면 인증을 건너뜀. 본 요청 인증은 그대로임을 테스트로 고정 ([85287e8](https://github.com/inha-likelion-team2/mildang-server/commit/85287e8)) |
| 흥정을 닫으면 이미 기록한 항목이 되살아나 이중 차감 | 대화창이 항목의 종착 전이보다 오래 살아, 기록된 항목이 흥정 완료 상태로 돌아갔습니다 | 흥정 종료에 항목 상태 가드 추가. 배포 전 감사에서 찾은 치명 3건·배포 블로커 3건과 함께 수정 ([c8bbde2](https://github.com/inha-likelion-team2/mildang-server/commit/c8bbde2)) |
| 기본 설문으로 주 예산 70, 라면(80) 한 번에 예산 소진 | 설문 가중치가 명세 예시에서 역산돼 참조 스케일(칼국수 80)과 맞지 않았습니다. "줄이며 버티는" 컨셉과 충돌 | 가중치를 참조 스케일 × 주간 빈도 중앙값으로 재산정해 주 190. 기획·AI·프론트에 진단 문서 공유 ([IMPROVEMENTS](docs/IMPROVEMENTS.md)) |

## 실행

```bash
./gradlew bootRun   # local 프로필: H2 인메모리 · AI는 Fake 응답 → 키 없이 뜹니다
./gradlew test
```

- `GET http://localhost:8080/v1/health` → `{"status":"ok"}`
- 브라우저 데모 화면 `http://localhost:8080/v1/app/index.html`에서 온보딩부터 리포트까지 전 흐름을 볼 수 있습니다.
- 실제 AI 서버와 붙이려면 `AI_FAKE=false`. Windows에서 경로에 한글이 있으면 Gradle 테스트가 깨지니 영문 경로에 클론하세요.

## 한계

- 결제 재요청 방어는 "조회 후 저장"이라 거의 동시에 들어온 두 요청은 조회 사이에 낄 수 있습니다. 토스의 `Idempotency-Key`가 승인은 한 번으로 막지만, 서버 쪽 유니크 제약은 걸지 않았습니다.
- 공모전 기간에 맞춰 만든 서버라 CI(GitHub Actions)가 없습니다. 테스트는 로컬에서 돌렸습니다.
- 제출 빌드는 `demo` 프로필이라 심사용 도구(`/demo/*`)가 열려 있고 일부 연동이 목 처리돼 있습니다(이유는 [배포 문서](docs/배포-Railway.md) 1절).

## 문서

- [API 명세서](docs/API-명세서.md) — API 계약의 정본(코드에 구현된 것만)
- [개발 가이드](docs/개발-가이드.md) — 브라우저 테스트, 프로필·환경변수, 패키지 구조, 핵심 규칙, 심사용 시드
- [배포(Railway)](docs/배포-Railway.md) — 프로필 선택, 환경변수, DB 변수 참조
- [DB 변경 v3.1](docs/DB-변경-v3.1.md) · [개선 제안](docs/IMPROVEMENTS.md)
