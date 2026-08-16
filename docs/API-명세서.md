# 밀당 API 명세서

> **이 파일이 API 계약의 정본입니다.** 코드에서 실제로 구현된 것만 적혀 있습니다.
> 백엔드가 API를 바꾸면 같은 커밋에서 이 파일을 갱신하고, 노션 명세서는 이 파일을 붙여넣어 갱신합니다.
> 기준 코드: `inha-likelion-team2/mildang-server` main · 엔드포인트 34개

## 개정 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-16 | `POST /items` **중복 제출 병합** 추가 — 같은 메뉴·같은 값의 확정 전 항목이 3초 안에 또 들어오면 기존 항목 반환 (이슈 #1 추가 피드백: IME Enter 이중 발생) |
| 2026-08-15 | **문서 신설** — 코드 전수 조사 기준으로 재작성. 이슈 #1 대응분 반영: `current.today` 신설 · `scans.menus[].item` 신설 · `item.source.scanId/menuId` 노출 · `POST /items` 스캔 항목 재사용 규칙 · 읽을 수 없는 본문 400 |

---

## 0. 공통 규격

### 0.1 기본

| 항목 | 값 |
|------|-----|
| Base URL | `https://<host>/v1` (로컬 `http://localhost:8080/v1`) |
| Content-Type | `application/json` — 업로드만 `multipart/form-data` |
| 인코딩 | **UTF-8 고정.** 다른 인코딩으로 본문을 보내면 400 |
| 인증 헤더 | `Authorization: Bearer <accessToken>` |
| 시각 | ISO 8601 UTC (`2026-08-15T12:05:13Z`). **일자 판정만 KST 05:00 경계** |
| 포인트 | 정수 **0~999**. 소수 없음. `pm`(오차범위)도 정수 — 잔액만 음수 허용 |
| ID | `{prefix}_{ULID}` — `usr` `chl` `itm` `anl` `hgl` `chk` `scn` `pay` `tip`. 스캔 메뉴만 예외로 `mnu_{번호}` |
| CORS | 데모는 전 오리진 허용 (실서비스 전 FE 도메인으로 좁힐 예정) |

### 0.2 인증 불필요 엔드포인트

`POST /auth/social` · `POST /auth/refresh` · `GET /plans` · `GET /health` · `GET /invites/{code}` · `GET /demo/ping`

그 외는 전부 `Authorization` 헤더가 필요합니다. 없거나 형식이 틀리면 **401 `TOKEN_INVALID`**, 만료면 **401 `TOKEN_EXPIRED`**(→ refresh).

> `/demo/*`는 `ping`을 빼면 **전부 인증이 필요합니다.**

### 0.3 응답 봉투

**성공은 봉투가 없습니다.** 데이터를 그대로 돌려줍니다.

**실패는 `error` 하나로 통일됩니다.**

```json
{
  "error": {
    "code": "ITEM_ALREADY_RECORDED",
    "message": "이미 확정된 항목은 지울 수 없어요.",
    "field": "menuId",
    "detail": {}
  }
}
```

- `message`는 **그대로 사용자에게 보여줘도 되는 한국어**입니다. 프론트에서 코드별 문구를 따로 만들 필요가 없습니다.
- `field`·`detail`은 있을 때만 옵니다.

### 0.4 날짜 경계 — 매일 05:00 KST

**자정이 아니라 새벽 5시가 하루의 경계입니다.** 새벽 3시에 먹은 라면은 전날 지출로 잡힙니다(야식 시나리오). `logicalDate`·`today.date`·항목 만료·배치 3종이 전부 이 기준입니다.

### 0.5 에러 코드 전체

| HTTP | code | 기본 메시지 | 발생 지점 |
|------|------|------------|----------|
| 400 | `VALIDATION_FAILED` | 요청 값이 올바르지 않습니다. | 타입·범위 위반, 소스 중복/누락, 읽을 수 없는 본문 |
| 401 | `TOKEN_EXPIRED` | 로그인이 만료됐어요. 다시 이어갈게요. | access 만료 → refresh |
| 401 | `TOKEN_INVALID` | 다시 로그인해 주세요. | 헤더 없음·위변조·refresh 실패 → 재로그인 |
| 402 | `PAYMENT_REQUIRED` | 이 기간은 결제가 필요해요. | W2·W4를 결제 없이 생성 |
| 403 | `FREE_TRIAL_USED` | 무료 체험을 이미 쓰셨어요. | 무료 1주 소진 후 재시작 |
| 404 | `NOT_FOUND` | 찾을 수 없어요. | 리소스 없음 / 진행 중 챌린지 없음 |
| 409 | `CHALLENGE_IN_PROGRESS` | 진행 중인 챌린지가 있어요. | 챌린지 중복 생성 |
| 409 | `BUDGET_ALREADY_SET` | 이미 예산이 확정된 챌린지입니다. | 예산 재확정 |
| 409 | `CHALLENGE_NOT_COMPLETED` | 아직 완주 전이에요. | 완주 전 리포트 조회 |
| 409 | `ITEM_ALREADY_RECORDED` | 이미 기록된 항목이에요. | 확정 항목 삭제·선차감·흥정 시도 |
| 409 | `HAGGLE_TURN_EXCEEDED` | 흥정은 10턴까지예요. 이제 정리해 볼까요? | 11번째 턴 (**429 아님**) |
| 409 | `HAGGLE_SESSION_CLOSED` | 이미 끝난 흥정이에요. | 닫힌 세션에 메시지 |
| 409 | `ITEM_EXPIRED` | 지난 항목으로는 흥정을 열 수 없어요. | EXPIRED 항목으로 세션 시작 |
| 409 | `PAYMENT_ALREADY_USED` | 이미 사용된 결제예요. | 결제 1건을 두 챌린지에 사용 |
| 413 | `IMAGE_TOO_LARGE` | 이미지는 10MB 이하로 올려주세요. | 업로드 크기 초과 |
| 422 | `ANALYSIS_FAILED` | 잘 모르겠어요 — 비슷한 걸 골라주세요 | 메뉴 식별·추출 실패 |
| 500 | `INTERNAL_ERROR` | 문제가 생겼어요. 잠시 뒤에 다시 시도해 주세요. | 서버 오류 |
| 503 | `AI_UNAVAILABLE` | 밀당이가 잠깐 자리를 비웠어요. 곧 돌아올게요. | AI 서비스 장애 |

> ⚠ **`RATE_LIMITED`(429)와 `PAYMENT_VERIFICATION_FAILED`(402)는 코드에 정의만 있고 실제로 던지는 곳이 없습니다.** 레이트 리밋은 현재 빌드에 구현돼 있지 않으므로 **429는 절대 오지 않습니다.** 프론트에 429 재시도 로직을 넣지 마세요.

### 0.6 열거형

| 이름 | 값 |
|------|-----|
| `Period` | `W1`(7일·무료) `W2`(14일·2,000원) `W4`(28일·3,500원) |
| `ChallengeStatus` | `ONBOARDING` `ACTIVE` `COMPLETED` `ABANDONED` |
| `ItemStatus` | `PENDING` `HAGGLED` `EXPIRED` `RECORDED` `PREPAID` `CANCELED` |
| `ItemKind` | `MEAL`(식사) `PROMISE`(약속) |
| `SourceType` | `TEXT` `IMAGE` `PRESET` |
| `Confidence` | `CERTAIN`(확실) `HIGH`(높음) `MEDIUM`(보통) |
| `OptionKey` | `HARD` `AS_IS` `EASY` (CUSTOM 폐지) |
| 설문 값 | **`"0-1"` `"2-3"` `"4+"`** — 통신 값이 이 문자열입니다 |
| `Weekday` | `MON` `TUE` `WED` `THU` `FRI` `SAT` `SUN` |
| `ConditionKey` | `BLOAT`(더부룩함) `SKIN`(피부) `DROWSY`(낮 졸림) |
| `ConditionValue` | `GOOD` `MID` `BAD` |
| `PaymentStatus` | `PAID` `FAILED` `REFUNDED` |
| `entryPoint` | `FREE` `PROMISE` `SCAN` |
| `frame` | `SAVE` `REDUCE_OVERFLOW` |
| `lever` | `AMOUNT` `COMPOSITION` — **2종만** (조리법 없음) |
| 흥정 세션 `status` | `OPEN` `CLOSED` `ABANDONED` |
| `pace.state` | `AHEAD` `ON_TRACK` `BEHIND` |
| `tip.basis` | `OVERSPEND_PATTERN` `RECENT_WIN` `PACE_AHEAD` `PACE_BEHIND` `CHECKIN_CORRELATION` `GENERIC` |
| `stats[].key` | `TOTAL_SPENT` `VS_BUDGET` `PEAK_SLOT` |

### 0.7 데모 모드 표시

`local`·`demo` 프로필에서 목으로 처리된 응답에만 최상위에 `"mocked": true`가 붙습니다 — `POST /auth/social` · `POST /auth/refresh` · `POST /payments/checkout` · `POST /.../share-card` · `/demo/*`. prod에서는 필드가 사라집니다.

> ⚠ 알려진 불일치: **share-card는 prod에서도 `mocked: true`가 붙습니다** (하드코딩). 실서비스 전환 시 정리 예정.

---

## 1. 절대 규칙 (프론트가 알아야 하는 것)

1. **잔액이 바뀌는 건 `record`·`prepay` 두 곳뿐입니다.** 흥정 종료(`close`)는 값만 바꾸고 잔액을 건드리지 않습니다.
2. **예산은 기간 총액 하나입니다.** 주차별 잔액 개념이 없습니다. 항등식 `balance = total − spent − prepaid`가 항상 성립합니다.
3. **`original`은 절대 안 바뀝니다.** 흥정 결과는 `adjusted`에만 들어가고, 표시할 값은 항상 **`effective.points`**입니다.
4. **초과(잔액 음수)는 항상 허용됩니다.** 잔액이 부족하다고 기록이나 흥정을 거절하지 않습니다.
5. **멱등**: 같은 종착 상태로 다시 요청하면 200 + `alreadyProcessed: true`입니다(409 아님). `true`면 성공 토스트를 띄우지 마세요.
6. **`record`·`prepay` 응답에 `budget`이 들어 있습니다.** 대시보드를 다시 부를 필요가 없습니다.
7. **흥정 턴은 서버가 셉니다.** 최대 10턴, 초과 시 409.

---

## 2. 인증 (`/auth`) — 인증 불필요

### `POST /auth/social` → 200

최초 호출 시 자동 가입됩니다. 데모에서는 카카오 검증을 하지 않고 **`idToken` 문자열 자체가 계정 키**입니다(`demo-judge-02`로 로그인하면 항상 같은 계정).

```json
// 요청
{ "provider": "KAKAO", "idToken": "...", "deviceId": "device-uuid", "pushToken": "..." }
```

| 필드 | 필수 | 비고 |
|------|------|------|
| `provider` | ✅ | **`KAKAO`만** 지원 (다른 값은 400) |
| `idToken` | ✅ | 데모에서는 계정 키로 그대로 사용. 비어 있으면 401 |
| `deviceId` | ✅ | **기기 = 세션.** 같은 기기로 다시 로그인하면 기존 세션 행이 회전됩니다 |
| `pushToken` | — | 없어도 됩니다 |

```json
// 응답 200
{
  "mocked": true,
  "accessToken": "eyJ...",
  "refreshToken": "rt_...",
  "expiresIn": 1800,
  "user": { "id": "usr_...", "nickname": "심사위원2", "isNew": true, "freeTrialUsed": false }
}
```

- `expiresIn`은 **초 단위**입니다 (access 30분 / refresh 60일).
- `isNew: true`면 온보딩(화면 1)으로, `false`면 대시보드로 보내면 됩니다.
- 닉네임은 `demo-judge-N` 계정이면 `심사위원N`, 그 외에는 `게스트####`입니다.

### `POST /auth/refresh` → 200

```json
// 요청
{ "refreshToken": "rt_..." }
```

응답은 `POST /auth/social`과 같은 형태입니다(`isNew`는 항상 `false`). 실패하면 **401 `TOKEN_INVALID`** — 재로그인시켜야 합니다.

---

## 3. 챌린지 · 예산 (`/plans`, `/challenges`)

### `GET /plans` → 200 (인증 불필요, 토큰은 선택)

토큰을 함께 보내면 무료 체험 소진 여부가 반영됩니다. 안 보내도 200입니다.

```json
{
  "plans": [
    { "period": "W1", "title": "맛보기 한 판", "subtitle": "최초 1회 무료 · 처음이라면 추천",
      "priceKrw": 0, "recommended": true, "available": true, "unavailableReason": null },
    { "period": "W2", "title": "제대로 한 판", "subtitle": "리포트가 뚜렷해지는 최소 기간",
      "priceKrw": 2000, "recommended": false, "available": true, "unavailableReason": null },
    { "period": "W4", "title": "장기전", "subtitle": "주차별로 예산을 나눠 드려요",
      "priceKrw": 3500, "recommended": false, "available": true, "unavailableReason": null }
  ],
  "notice": "결제는 프리미엄이 아니라, 진짜 할 건지 확인하는 문턱이에요."
}
```

- ⚠ `title`은 **"1주"가 아니라 "맛보기 한 판"** 같은 문구입니다. 기간 표기(1주/2주/4주)가 필요하면 `period`로 프론트에서 매핑하세요.
- ⚠ W4의 `subtitle`이 "주차별로 예산을 나눠 드려요"인데 **v1.3에서 주차별 예산은 폐지**됐습니다(총액 단일). 문구 수정이 필요합니다 — 기획 확인 대기.
- 무료 1주를 이미 쓴 계정은 `W1`이 `available: false` + `unavailableReason: "무료 체험을 이미 쓰셨어요"`.
- `recommended`는 W1에만 `true`입니다.
- **데모에서는 무료 소진이 무시됩니다**(심사위원 무제한 재시작).
- 에러가 없는 엔드포인트입니다.

### `POST /challenges` → **201**

```json
// 요청
{ "period": "W2", "paymentId": "pay_..." }
```

```json
// 응답 201
{ "id": "chl_...", "period": "W2", "status": "ONBOARDING",
  "totalDays": 14, "needsSurvey": true, "startedAt": null }
```

- ⚠ **`startedAt`은 여기서 항상 `null`**입니다. 예산 확정 시점에 정해집니다.
- `needsSurvey: false`면 이전 챌린지의 설문을 재사용할 수 있어 화면 2에서 설문을 건너뛸 수 있습니다.

| 에러 | 조건 |
|------|------|
| 409 `CHALLENGE_IN_PROGRESS` | `ONBOARDING`·`ACTIVE` 챌린지가 이미 있음 |
| 402 `PAYMENT_REQUIRED` | W2·W4인데 `paymentId`가 없거나, 결제가 없거나 남의 것이거나 `PAID`가 아님 |
| 409 `PAYMENT_ALREADY_USED` | 그 결제를 이미 다른 챌린지에 씀 |
| 400 `VALIDATION_FAILED` (`paymentId`) | "결제한 기간과 선택한 기간이 달라요." |
| 403 `FREE_TRIAL_USED` | 무료 소진 후 W1 (데모에서는 발생하지 않음) |

### `POST /challenges/{id}/budget/estimate` → 200

**저장하지 않습니다.** 화면 2의 카드를 그리기 위한 계산만 합니다.

```json
// 요청
{ "survey": { "noodle": "2-3", "bread": "0-1", "snack": "4+" } }
```

- 세 항목 모두 필수. 값은 **`"0-1"` `"2-3"` `"4+"`** 세 가지뿐이고, 다른 문자열은 400입니다.

```json
// 응답 200
{
  "estimatedWeekly": 250,
  "recommended": 225,
  "cutRatePercent": 15,
  "rationale": "...",
  "anchors": [ { "label": "라면 한 번", "points": 80 },
               { "label": "김밥 한 줄", "points": 20 },
               { "label": "삼겹살", "points": 0 } ],
  "options": [
    { "key": "HARD",  "label": "더 빡세게", "budget": 188, "totalBudget": 376, "note": "..." },
    { "key": "AS_IS", "label": "이대로",   "budget": 225, "totalBudget": 450, "note": null },
    { "key": "EASY",  "label": "여유있게", "budget": 238, "totalBudget": 476, "note": null }
  ],
  "totalBudget": 450
}
```

- ⚠ **카드에는 `totalBudget`(기간 총액)을 표시하세요.** `budget`은 주간값이라 W2·W4에서 실제 예산과 다릅니다.
- 옵션은 항상 HARD·AS_IS·EASY 3장(감축률 25/15/5%), 전 기간 공통입니다. 직접 입력(CUSTOM)은 없습니다.
- 최상위 `recommended`·`cutRatePercent`·`totalBudget`은 AS_IS 기준입니다.
- `anchors`는 고정 3개입니다("이 예산이면 라면 몇 번" 감을 주는 용도).
- `note`는 HARD에만 있고 나머지는 `null`입니다.

| 에러 | 조건 |
|------|------|
| 404 `NOT_FOUND` | 챌린지 없음·남의 것 |
| 409 `BUDGET_ALREADY_SET` | 이미 `ONBOARDING`이 아님 |
| 400 `VALIDATION_FAILED` (`survey`) | "설문 값은 0-1 / 2-3 / 4+ 중 하나예요." |

### `POST /challenges/{id}/budget` → 200

```json
// 요청
{ "survey": { "noodle": "2-3", "bread": "0-1", "snack": "4+" },
  "optionKey": "AS_IS", "budget": 225 }
```

- **`budget`은 주간값**을 보냅니다(estimate의 `options[].budget`). 서버가 곱수를 적용해 총액으로 저장합니다.
- **`survey`는 선택입니다.** 생략하면 이전 챌린지 설문을 재사용합니다(첫 챌린지에서 생략하면 400).

```json
// 응답 200
{ "id": "chl_...", "status": "ACTIVE", "period": "W2",
  "budget": 450, "balance": 450,
  "startedAt": "...", "endsAt": "...",
  "startTip": { "text": "..." } }
```

- **응답의 `budget`·`balance`는 기간 총액**입니다(요청은 주간값, 응답은 총액).
- `startTip`은 설문 27조합 하드코딩이라 AI 호출 없이 항상 즉시 옵니다.

| 에러 | 조건 |
|------|------|
| 409 `BUDGET_ALREADY_SET` | 이미 확정됨 |
| 400 `VALIDATION_FAILED` (`budget`) | "예산이 제안값과 달라요." — estimate 값과 불일치 |
| 400 `VALIDATION_FAILED` (`survey`) | 첫 챌린지인데 설문 생략 |

### `GET /challenges/current` → 200

**이 응답 하나로 메인 화면이 전부 그려집니다.** 진행 중 챌린지가 없으면 **404 "진행 중인 챌린지가 없어요."**입니다(→ 화면 1로).

```json
{
  "challenge": { "id": "chl_...", "period": "W1", "status": "ACTIVE",
                 "dayIndex": 4, "totalDays": 7, "label": "1주 챌린지 · 4일차" },
  "budget": { "total": 225, "balance": 217, "spent": 8, "prepaid": 0, "gaugePercent": 96 },
  "pace": { "expectedBalance": 96, "diff": 121, "note": "페이스보다 +121 앞서 있어요", "state": "AHEAD" },
  "weekly": null,
  "tip": { "id": "tip_...", "text": "...", "basis": "RECENT_WIN" },
  "today": {
    "date": "2026-08-15", "count": 1, "totalPoints": 8,
    "items": [ { "id": "itm_...", "name": "제육볶음", "label": "절반", "points": 8,
                 "haggled": true, "kind": "MEAL", "recordedAt": "2026-08-15T12:05:13Z" } ]
  },
  "prepaidItems": [ { "id": "itm_...", "name": "금요일 치킨 약속", "points": 70,
                      "weekday": "FRI", "note": "사전 결재 · 예산에서 미리 빼뒀어요" } ],
  "checkin": { "doneToday": false, "dueAt": "2026-08-15T13:00:00Z" },
  "expiredConfirm": [ { "id": "itm_...", "logicalDate": "2026-08-14", "menuLabel": "라면 반봉지",
                        "points": 40, "question": "8월 14일에 40으로 합의한 라면, 드셨어요?" } ]
}
```

| 필드 | 규칙 |
|------|------|
| `challenge.label` | **일차 문구가 이미 포함**돼 있습니다(`1주 챌린지 · 4일차`). 프론트에서 또 붙이지 마세요 |
| `budget.balance` | **선차감이 이미 반영된 값**입니다. `prepaid`를 다시 빼면 안 됩니다 |
| `budget.gaugePercent` | 0~100. 잔액이 음수여도 0으로 클램프 |
| `pace.state` | `AHEAD`/`ON_TRACK`/`BEHIND` — 부호로만 판정(임계 없음) |
| `pace.note` | 그대로 노출 가능한 문구 |
| `weekly` | **항상 `null`입니다** (총액 단일 모델이라 주차 개념이 없음) |
| `tip` | AI 생성, 일 1회. **생성 실패 시 `null`** → 영역을 숨기세요 |
| `today` | **오늘 먹은 것** — 아래 참조 |
| `prepaidItems[]` | 선차감된 약속 카드 (전체) |
| `checkin.doneToday` | `false`면 체크인 버튼에 뱃지. `dueAt`은 오늘 22:00 KST |
| `expiredConfirm[]` | 어제 흥정만 하고 기록 안 한 항목, **최대 3건**. `question`을 그대로 띄우고 "드셨어요"→`record` / "안 먹었어요"→`DELETE` |

**`today` 상세**

| 필드 | 규칙 |
|------|------|
| `date` | 논리적 날짜(05:00 경계). 새벽 3시에 먹으면 전날로 잡힙니다 |
| `count` · `totalPoints` | `items`의 개수·`points` 합 |
| `items[]` | **오늘 기록(`RECORDED`)된 항목만**, `recordedAt` 오름차순 |
| `items[].label` | 흥정했으면 합의 표현("절반"), 아니면 단위("1인분") |
| `items[].points` | **effective** — 흥정했으면 합의값 |
| `items[].haggled` | 흥정 배지 표시용 |

- 기록이 없으면 `{"count": 0, "totalPoints": 0, "items": []}`입니다(필드 자체는 항상 있음).
- 선차감(`PREPAID`)은 여기 안 들어옵니다. `prepaidItems`에 있다가 약속 요일이 지나면 배치가 `RECORDED`로 넘기고, 그날 `today`에 나타납니다.

> **완주 후에는 이 API가 404입니다.** (기간이 끝나면 `COMPLETED`로 전환하고 404를 던집니다.) 마지막 챌린지를 조회하는 API가 없으므로, 프론트가 `challengeId`를 로컬에 보관했다가 `GET /challenges/{id}/report`로 진입해야 합니다.

---

## 4. 결제 (`/payments`)

### `POST /payments/checkout` → **201**

```json
// 요청
{ "period": "W2", "provider": "MOCK", "receipt": "..." }
```

- `provider`: **`MOCK`**(데모 전용) · `IAP_APPLE` · `IAP_GOOGLE`
- 데모에서는 검증을 건너뛰고 **항상 `PAID`**입니다(실감을 위해 800ms 지연).
- 같은 `receipt`로 다시 부르면 기존 결제를 그대로 돌려줍니다(빈 `receipt`는 제외).

```json
// 응답 201
{ "mocked": true, "id": "pay_...", "period": "W2", "amountKrw": 2000,
  "status": "PAID", "paidAt": "..." }
```

여기서 받은 `id`를 `POST /challenges`의 `paymentId`로 넘기면 됩니다.

| 에러 | 조건 |
|------|------|
| 400 `VALIDATION_FAILED` (`period`) | "1주 챌린지는 결제가 필요 없어요." |
| 400 `VALIDATION_FAILED` (`provider`) | 지원하지 않는 결제 수단 |

---

## 5. 항목 (`/items`, `/presets`)

3a(약속)·3b(식사) 공용 리소스이고 `kind`로 구분합니다.

### 항목 객체

```json
{
  "id": "itm_...",
  "kind": "MEAL",
  "status": "HAGGLED",
  "source": { "type": "IMAGE", "scanId": "scn_...", "menuId": "mnu_3" },
  "original": { "name": "제육볶음", "unit": "1인분", "points": 15, "pm": 5,
                "confidence": "MEDIUM", "basis": "시판 고추장 베이스로 추정" },
  "adjusted": { "label": "절반", "points": 8, "basis": "양 절반",
                "haggleId": "hgl_...", "turns": 1 },
  "effective": { "points": 8, "balanceAfter": 217, "balanceIfOriginal": 210 },
  "weekday": null,
  "logicalDate": "2026-08-15",
  "createdAt": "...", "expiresAt": "2026-08-16T20:00:00Z", "recordedAt": null
}
```

| 필드 | 규칙 |
|------|------|
| `source.type` | `TEXT`(직접 입력) `IMAGE`(스캔) `PRESET`(자주 먹는 것) |
| `source.refId` | `TEXT`→`anl_*` · `PRESET`→`pst_*`. **`IMAGE`에는 없습니다** |
| `source.scanId`·`menuId` | **`IMAGE`만.** `mnu_N`이 스캔 안에서만 유효한 키라 쌍으로 참조합니다 |
| `original` | **절대 안 바뀝니다.** 흥정해도 그대로 |
| `adjusted` | 흥정 전에는 `null` |
| `effective.points` | **화면에 표시할 값은 항상 이것입니다** (`adjusted?.points ?? original.points`) |
| `effective.balanceAfter` | 미차감 항목은 "기록하면 얼마가 될지" 예상값, 차감된 항목은 실제 스냅샷 |
| `effective.balanceIfOriginal` | "원래대로였다면" 비교용 |
| `weekday` | `PROMISE`만 값이 있습니다 |
| `expiresAt` | `MEAL`은 다음날 05:00 KST, `PROMISE`는 `null` |

### `GET /items?kind=&status=&limit=` → 200

| 파라미터 | 기본값 | 비고 |
|---------|--------|------|
| `kind` | 전체 | `MEAL`·`PROMISE` |
| `status` | `PENDING,HAGGLED` | 콤마 구분 다중 지정. 잘못된 값이면 400 |
| `limit` | 20 | 서버에서 **최대 50**으로 자름 |

```json
{ "items": [ /* 항목 객체 */ ],
  "summary": { "count": 3, "totalPoints": 55, "balanceAfterAll": 170 } }
```

- ⚠ **`summary`는 쿼리 필터와 무관하게 미기록 전체(`PENDING`·`HAGGLED`·`EXPIRED`) 고정값**입니다. 필터를 걸어도 값이 안 바뀝니다.
- 정렬은 `createdAt` 내림차순(최신 먼저)입니다.

### `POST /items` → **201**

소스는 **셋 중 정확히 하나**입니다.

```json
{ "kind": "MEAL", "analysisId": "anl_..." }                       // (A) 직접 입력
{ "kind": "MEAL", "scanId": "scn_...", "menuId": "mnu_3" }        // (B) 스캔 메뉴
{ "kind": "MEAL", "presetId": "pst_ramen" }                       // (C) 자주 먹는 것
{ "kind": "PROMISE", "analysisId": "anl_...", "weekday": "FRI" }  // 약속은 weekday 필수
```

- 응답은 **항목 객체**, 상태는 `PENDING`.
- ★ **잔액은 안 바뀝니다.** 생성은 목록에 담는 것일 뿐입니다.

> **(B) 스캔 소스의 항목 재사용 규칙**
> 같은 `scanId`+`menuId`+`kind`에 **아직 확정 전(`PENDING`·`HAGGLED`)인 항목이 있으면 새로 만들지 않고 그 항목을 반환**합니다. 스캔 메뉴 한 칸 = 살아있는 항목 하나입니다.
> 기록·선차감되거나 삭제된 뒤에 다시 부르면 그때는 새 항목이 만들어집니다(메뉴판 원값 기준).
> **덕분에 "밀당하기 → 기록하기" 사이에 항목 id를 들고 있지 않아도 됩니다.**

> **중복 제출 병합 (2026-08-16 신설)**
> 소스와 무관하게, **같은 메뉴·같은 값·같은 종류(+약속은 같은 요일)의 확정 전 항목이 3초 안에 이미 만들어졌으면 새로 만들지 않고 그 항목을 반환**합니다.
> 더블 탭이나 **한글 IME의 Enter 이중 발생**(조합 중 Enter는 `keydown`이 두 번 뜹니다)으로 같은 항목이 두 개 생기던 것을 막습니다. 분석을 두 번 해서 `analysisId`가 서로 달라도 병합됩니다.
> 3초가 지나면 정상적으로 새 항목이 만들어집니다 — 하루에 같은 메뉴를 두 번 먹는 건 막지 않습니다.
> ⚠ 이건 **안전망이지 대체재가 아닙니다.** 프론트에도 중복 제출 가드를 넣어주세요 (§12 체크리스트).

| 에러 | 조건 |
|------|------|
| 400 `VALIDATION_FAILED` (`source`) | "analysisId / scanId+menuId / presetId 중 정확히 하나만 보내주세요." |
| 400 `VALIDATION_FAILED` (`weekday`) | "약속에는 요일이 필요해요." |
| 400 `VALIDATION_FAILED` (`menuId`) | `scanId`만 보냈거나 `mnu_N` 형식이 아님 |
| 404 `NOT_FOUND` | "분석 결과를 찾을 수 없어요. 다시 분석해 주세요." / **"분석이 만료됐어요. 다시 분석해 주세요."**(30분 TTL) / 프리셋·스캔 메뉴 없음 |

### `POST /items/{id}/record` → 200 — 기록 (즉시 차감)

```json
{
  "item": { /* 항목 객체 */ },
  "budget": { "total": 225, "balance": 217, "spent": 8, "prepaid": 0, "gaugePercent": 96 },
  "overflow": null,
  "alreadyProcessed": false
}
```

- **`budget`이 응답에 들어 있으니 대시보드를 다시 부르지 마세요.**
- 결과 잔액이 음수일 때만 `overflow`가 붙습니다:
  `{ "balance": -18, "originalWouldBe": -25, "reducedBy": 7, "note": "..." }`
  `note`는 판정·질책 없는 문구라 그대로 노출해도 됩니다. **초과 기록은 항상 허용됩니다.**

| 현재 상태 | 결과 |
|-----------|------|
| `PENDING` `HAGGLED` `EXPIRED` | 차감 → `RECORDED`, `alreadyProcessed: false` |
| `RECORDED` | 200 멱등, `alreadyProcessed: true` (중복 차감 없음) |
| `PREPAID` | 200 전이, `alreadyProcessed: true` — **잔액 불변** (`prepaid`→`spent` 이동) |
| `CANCELED` | 404 |

### `POST /items/{id}/prepay` → 200 — 선차감 (약속 전용)

응답 구조는 `record`와 같습니다.

| 현재 상태 | 결과 |
|-----------|------|
| `PENDING` `HAGGLED` | 차감 → `PREPAID`, `alreadyProcessed: false` |
| `PREPAID` | 200 멱등, `alreadyProcessed: true` |
| `RECORDED` `EXPIRED` `CANCELED` | 409 `ITEM_ALREADY_RECORDED` |

- `kind`가 `PROMISE`가 아니면 **400** "선차감은 약속 항목에만 할 수 있어요."

### `DELETE /items/{id}` → **204** (본문 없음)

- 확정된 항목(`RECORDED`·`PREPAID`)은 **409 `ITEM_ALREADY_RECORDED`** — "이미 확정된 항목은 지울 수 없어요."
- 이미 삭제된 항목은 204 멱등입니다.
- 소프트 삭제(`CANCELED`)라 목록에서만 사라집니다.

### `GET /presets` → 200 — 자주 먹는 것 (정확히 4개)

```json
{
  "presets": [
    { "id": "pst_ramen",   "name": "라면",   "unit": "1봉지", "points": 80, "pm": 10 },
    { "id": "pst_bread",   "name": "빵",     "unit": "1개",   "points": 45, "pm": 10 },
    { "id": "pst_tteok",   "name": "떡볶이", "unit": "1인분", "points": 55, "pm": 10 },
    { "id": "pst_chicken", "name": "치킨",   "unit": "1마리", "points": 70, "pm": 10 }
  ],
  "source": "DEFAULT"
}
```

- 현재 `source`는 **항상 `DEFAULT`**입니다(이력 기반 `HISTORY` 집계는 미구현).
- ⚠ 표시 가격은 **항상 `original.points`**입니다. 과거 합의값을 칩에 쓰면 기준선이 계속 내려가서 그렇습니다(합의값은 흥정 오프닝 대사에서만 언급).

---

## 6. AI 분석 (`/analyses`)

### `POST /analyses/text` → 200 — 텍스트로 메뉴 추정

```json
// 요청
{ "query": "라면", "context": { "challengeId": "chl_...", "kind": "MEAL" } }
```

- `query`는 **1~40자**입니다.
- ⚠ **잔액·남은 끼수는 보내지 않습니다.** 서버가 `challengeId`로 조회합니다.
- `challengeId`가 진행 중 챌린지가 아니면 404입니다.

```json
// 응답 200 — 식별 성공
{
  "id": "anl_...",
  "resolved": true,
  "menu": { "name": "라면", "unit": "1봉지", "points": 80, "pm": 10,
            "confidence": "HIGH", "basis": "봉지라면 1인분 기준" },
  "candidates": null,
  "expiresAt": "2026-08-15T13:00:00Z"
}
```

- **`unit`은 흥정의 출발점**이라 반드시 옵니다("1봉지"가 있어야 "반봉지"를 협상합니다).
- `basis`는 40자 이내 한 문장, 기준 수량이 들어 있습니다.
- **`expiresAt`은 30분 뒤**입니다. 지난 `analysisId`로 항목을 만들면 404 — 재분석해야 합니다.

> ⚠ **식별 실패는 200이 아니라 422 에러 응답입니다.** 후보는 `error.detail.candidates`에 들어 있습니다.

```json
// 응답 422 — 식별 실패
{
  "error": {
    "code": "ANALYSIS_FAILED",
    "message": "잘 모르겠어요 — 비슷한 걸 골라주세요",
    "detail": {
      "candidates": [
        { "name": "카레라이스", "points": 30, "pm": 10, "confidence": "MEDIUM" },
        { "name": "카레우동",   "points": 70, "pm": 15, "confidence": "MEDIUM" },
        { "name": "카레빵",     "points": 45, "pm": 10, "confidence": "HIGH" }
      ]
    }
  }
}
```

- 후보는 **정확히 3개**입니다. 칩으로 보여주고 고르게 하면 됩니다.
- AI 검증 게이트를 두 번 실패하면 `candidates`가 **빈 배열**로 옵니다 — 이 경우 재입력을 유도하세요.
- AI 자체가 죽었으면 **503 `AI_UNAVAILABLE`**입니다.

### `GET /analyses/recent` → 200 — 최근 입력 칩 (최대 3개)

```json
{ "recent": [ { "name": "라면" }, { "name": "치킨" } ] }
```

최근 20건 중 중복 없는 이름 3개, 최신순입니다. 에러가 없습니다.

---

## 7. 스캔 (`/scans`)

### `POST /scans` → **201** (multipart)

| 파트/파라미터 | 비고 |
|--------------|------|
| `image` (파일 파트) | **JPEG·PNG만.** 10MB 초과 시 413 |
| `challengeId` (쿼리 파라미터) | 진행 중 챌린지가 아니면 404 |

- ⚠ **HEIC는 지원하지 않습니다** — 400 "JPEG/PNG 이미지를 올려주세요. (HEIC은 데모에서 미지원)". 웹에서는 `canvas` → JPEG blob으로 캡처하면 자연히 해결됩니다.
- ⚠ **실 AI 기준 20초 이상 걸립니다.** 로딩 UX가 필요합니다.
- 서버가 EXIF를 제거하고 긴 변 4096px로 재인코딩합니다.

```json
// 응답 201
{
  "id": "scn_...",
  "place": "김밥천국 성수점",
  "placeConfidence": "HIGH",
  "scannedAt": "...",
  "menus": [
    { "id": "mnu_3", "name": "제육볶음", "points": 15, "pm": 5,
      "confidence": "MEDIUM", "basis": "시판 고추장 베이스로 추정", "edited": false,
      "item": { "id": "itm_...", "status": "HAGGLED", "points": 8,
                "label": "절반", "haggled": true } }
  ],
  "recommendation": { "menuId": "mnu_3", "points": 15, "comment": "..." }
}
```

| 필드 | 규칙 |
|------|------|
| `menus[]` | **points 오름차순(싼 순) 정렬**, 최대 40개 |
| `menus[].id` | `mnu_{번호}` — **이 스캔 안에서만 유효한 키**입니다 |
| `menus[].points` | 메뉴판 추정값. **흥정해도 안 바뀝니다** |
| `menus[].edited` | 사용자가 가격을 수정했으면 `true` |
| `menus[].item` | 그 메뉴로 만들어져 **확정 전인 항목**. 없으면 `null` |
| `menus[].item.points` | **effective** — 흥정했으면 합의값 |
| `menus[].item.label` | 합의 표현. 흥정 전이면 필드 없음 |
| `recommendation` | 밀당이 추천 1개. 못 고르면 **`null`**, `comment`만 실패해도 `null`(영역 숨김) |

> **흥정 후 4b 화면 그리는 법**
> `item`이 있으면 그 행은 `item.points`로 그리고, 원래 가격(`menus[].points`)은 취소선으로 함께 보여주면 됩니다. `item`이 `null`이면 평소대로 `menus[].points`를 씁니다.
> 항목이 기록·선차감되거나 삭제되면 `item`은 다시 `null`이 됩니다.

| 에러 | 조건 |
|------|------|
| 400 `VALIDATION_FAILED` (`image`) | 빈 파일 / 읽기 실패 / HEIC 등 디코딩 불가 |
| 413 `IMAGE_TOO_LARGE` | 10MB 초과 |
| 422 `ANALYSIS_FAILED` | "메뉴를 읽지 못했어요 — 다시 찍어주세요." / "메뉴 가격을 매기지 못했어요 — 다시 찍어주세요." |
| 503 `AI_UNAVAILABLE` | AI 두 번 다 실패 |

### `GET /scans/{id}` → 200 — 재진입

`POST /scans`와 **완전히 같은 구조**입니다. 흥정을 마치고 4b로 돌아올 때 이걸 다시 불러 `menus[].item`을 갱신하세요.

### `PATCH /scans/{id}/menus/{menuId}` → 200 — 가격 수기 수정

```json
// 요청
{ "points": 25 }
```

- `points`는 **0~999** 필수.

```json
// 응답 200 — 수정된 행 하나
{ "id": "mnu_3", "name": "제육볶음", "points": 25, "pm": 0,
  "confidence": "CERTAIN", "basis": "직접 입력한 값", "edited": true, "item": null }
```

- 수정하면 `pm: 0` · `confidence: CERTAIN` · `basis: "직접 입력한 값"` · `edited: true`로 고정됩니다.
- ⚠ **이 응답의 `item`은 항상 `null`입니다.** 항목 상태가 필요하면 `GET /scans/{id}`를 다시 부르세요.
- ⚠ **이미 만들어진 항목에는 소급되지 않습니다.** 수정 후 새로 만든 항목부터 반영됩니다.

---

## 8. 흥정 (`/haggles`)

### `POST /haggles` → **201** — 세션 시작

```json
// 요청
{ "itemId": "itm_...", "entryPoint": "SCAN" }
```

- `entryPoint`: `FREE`(3b) · `PROMISE`(3a) · `SCAN`(4b) — 오프닝 대사와 칩이 달라집니다. 다른 값은 400.

```json
// 응답 201
{
  "id": "hgl_...", "itemId": "itm_...", "entryPoint": "SCAN",
  "maxTurns": 10, "turn": 0,
  "target": { "name": "제육볶음", "unit": "1인분", "points": 15, "pm": 5, "place": null },
  "agreed": null,
  "balance": 225,
  "frame": "SAVE",
  "opening": "...",
  "chips": ["반만 먹을게", "양은 그대로", "더 깎아줘", "그대로 먹을래"],
  "status": "OPEN"
}
```

- `frame`: 잔액 ≥ 0이면 `SAVE`("얼마나 남길까"), 음수면 `REDUCE_OVERFLOW`("얼마나 덜 깊어질까"). **세션 개설 시 정해지고 대화 중에는 안 바뀝니다.**
- `chips` 4개는 서버 고정값입니다. 그대로 버튼으로 쓰면 됩니다.
- `target.place`는 스캔 항목이면 `null`, 그 외에는 `"집"`입니다.
- 직전에 같은 메뉴를 흥정한 적이 있으면 `opening`에 그 내용이 언급됩니다.
- 같은 항목에 열린 세션이 있으면 자동으로 버리고 새로 엽니다(항목당 열린 세션 1개).
- **AI가 죽어도 에러가 아닙니다** — 규칙 기반 오프닝으로 폴백합니다.

| 에러 | 조건 |
|------|------|
| 409 `ITEM_EXPIRED` | 항목이 `EXPIRED` |
| 409 `ITEM_ALREADY_RECORDED` | 항목이 `RECORDED`·`PREPAID`·`CANCELED` |
| 400 `VALIDATION_FAILED` (`entryPoint`) | 허용값이 아님 |

### `POST /haggles/{id}/messages` → 200 — 한 턴 진행

```json
// 요청
{ "text": "반만 먹을게" }
```

- `text`는 **1~200자**입니다. 스트리밍이 아니라 단건 응답입니다.

```json
// 응답 200
{
  "turn": 1, "maxTurns": 10, "turnsLeft": 9,
  "reply": { "text": "절반이면 8이에요. 어때요?", "hasProposal": true },
  "proposal": { "key": "half", "label": "절반", "points": 8,
                "lever": "AMOUNT", "basis": "양 절반" },
  "frame": "SAVE",
  "simulation": {
    "adjusted": { "row": "합의값 8", "balanceAfter": 217, "overflow": false },
    "original": { "row": "원래값 15", "balanceAfter": 210, "overflow": false }
  },
  "agreed": { "key": "half", "label": "절반", "points": 8 },
  "closeButtonLabel": "대화 종료 · 8으로 반영",
  "status": "OPEN"
}
```

| 필드 | 규칙 |
|------|------|
| `proposal` | 설명만 하는 턴이면 `null`입니다 |
| `proposal.lever` | `AMOUNT`(양) 또는 `COMPOSITION`(구성) — **2종뿐** |
| `proposal.points` | **항상 원래값 이하**입니다(게이트가 강제) |
| `agreed` | 현재까지의 합의. 새 제안이 없으면 직전 값이 유지됩니다 |
| `closeButtonLabel` | **서버가 만들어 줍니다. 그대로 버튼에 쓰세요** |
| `simulation` | 합의값/원래값 두 줄 비교 막대용. `overflow: true`면 음수 잔액 |

- **11번째 턴은 409 `HAGGLE_TURN_EXCEEDED`**입니다(429 아님).
- 닫힌 세션에 보내면 409 `HAGGLE_SESSION_CLOSED`.
- 턴 수는 서버가 셉니다. 클라이언트 카운트를 믿지 마세요.
- **AI가 죽어도 에러가 아닙니다** — 규칙 폴백(절반 제안)이 나갑니다.

### `POST /haggles/{id}/close` → 200 — 대화 종료 (합의값 반영)

```json
{
  "haggle": { "id": "hgl_...", "status": "CLOSED", "turns": 1, "closedAt": "..." },
  "item": { /* 항목 객체 — adjusted가 채워지고 status가 HAGGLED */ },
  "farewell": "거래 종료. 제육볶음 절반 8으로 항목을 고쳐뒀습니다. 아직 예산은 안 건드렸어요."
}
```

- ★ **잔액은 안 바뀝니다.** 합의값이 항목에 반영될 뿐이고, 차감은 출발 화면으로 돌아가 `record`/`prepay`를 눌러야 일어납니다.
- 합의 없이 닫으면 `adjusted`는 `null`로 남고 항목은 `PENDING` 그대로입니다.
- 이미 닫힌 세션에 다시 부르면 현재 상태를 그대로 돌려줍니다(멱등).
- 버리고 나간(`ABANDONED`) 세션이면 409 `HAGGLE_SESSION_CLOSED`.

### `DELETE /haggles/{id}` → **204** — 변경 없이 나가기 (✕)

- 세션은 버려지고 항목은 원래값 그대로입니다.
- 다시 흥정을 열 수 있습니다(턴 초기화).

---

## 9. 체크인 (`/checkins`)

### `GET /checkins/today` → 200

```json
{
  "date": "2026-08-15", "dayIndex": 4, "done": false,
  "answers": null,
  "questions": [ { "key": "BLOAT",  "label": "더부룩함", "desc": "식후 속 상태" },
                 { "key": "SKIN",   "label": "피부",     "desc": "트러블·건조" },
                 { "key": "DROWSY", "label": "낮 졸림",  "desc": "식곤증 정도" } ],
  "checkinDays": { "answered": 3, "elapsed": 4, "total": 7 }
}
```

- `answers`는 아직 제출 전이면 `null`입니다.
- `checkinDays`는 **원시 수치만** 줍니다(비율·임계 없음). "3/7일 기록" 같은 표기는 프론트 몫입니다.

### `PUT /checkins/today` → 200 — 멱등 덮어쓰기

```json
// 요청
{ "answers": { "BLOAT": "BAD", "SKIN": "MID", "DROWSY": "GOOD" } }
```

- ⚠ **키는 대문자 그대로**입니다(`BLOAT`·`SKIN`·`DROWSY`).
- **세 항목 모두 필수**입니다. 하나라도 빠지면 400.
- 같은 날 다시 보내면 덮어씁니다(에러 아님).

```json
// 응답 200
{ "id": "chk_...", "date": "2026-08-15", "done": true,
  "answers": { "BLOAT": "BAD", "SKIN": "MID", "DROWSY": "GOOD" },
  "message": "접수 완료. 오늘 장부는 닫습니다 — 내일 봬요.",
  "checkinDays": { "answered": 4, "elapsed": 4, "total": 7 } }
```

---

## 10. 리포트 · 공유

### `GET /challenges/{id}/report` → 200 — 완주 리포트

완주 전이면 **409 `CHALLENGE_NOT_COMPLETED`**입니다. (기간이 이미 지난 `ACTIVE` 챌린지는 자동으로 `COMPLETED` 전환 후 정상 응답합니다.)

```json
{
  "challenge": { "id": "chl_...", "period": "W1", "label": "1주 챌린지 · 완주", "completedAt": "..." },
  "title": "당신의 몸이 쓴 리포트",
  "stats": [
    { "key": "TOTAL_SPENT", "label": "총 소비",   "value": "80",  "sub": "/85" },
    { "key": "VS_BUDGET",   "label": "예산 대비", "value": "-5",  "sub": null },
    { "key": "PEAK_SLOT",   "label": "최다 소비", "value": "금 저녁", "sub": null }
  ],
  "finding": {
    "available": true,
    "headline": "...",
    "metric": { "conditionKey": "BLOAT", "thresholdPoints": 40, "ratio": 2.4 },
    "sampleNote": "표본이 작아 경향으로 봐주세요",
    "sample": { "answeredDays": 6, "totalDays": 7 }
  },
  "haggleHighlight": {
    "totalSaved": 40,
    "best": { "menu": "라면", "originalLabel": "1봉지 80", "adjustedLabel": "반봉지 40",
              "savedPoints": 40, "when": "금요일 저녁" },
    "avgTurns": 1.5, "longestTurns": 3
  },
  "disclaimer": "이 리포트는 의학적 진단이 아닌 본인 기록 기반 관찰입니다.",
  "nextChallenge": { "period": "W1", "optionKey": "HARD",
                     "suggestedBudget": 75, "ctaLabel": "재대결 받기 · 이번엔 75" }
}
```

| 필드 | 규칙 |
|------|------|
| `challenge.label` | **"완주" 문구 포함**. 중복 표기 주의 |
| `stats` | **항상 3개** — `TOTAL_SPENT` · `VS_BUDGET` · `PEAK_SLOT` 순서 고정 |
| `stats[].value` | **문자열**입니다. `VS_BUDGET`은 양수면 `+`가 붙고 음수는 그대로. 데이터가 없으면 `PEAK_SLOT`은 `"—"` |
| `finding.available` | `false`면 `headline`·`metric`·`sampleNote`가 전부 `null`로 옵니다. **발견 영역만 숨기고 나머지는 정상 표시** — 리포트는 항상 생성됩니다 |
| `finding.metric.thresholdPoints` | 항상 **40** |
| `finding.sampleNote` | 항상 동반해서 보여주세요 (N=1 데이터라서) |
| `haggleHighlight.best` | 흥정 이력이 없으면 `null` |
| `nextChallenge.suggestedBudget` | ⚠ **주간값**입니다. `ctaLabel`의 숫자는 기간 총액이라 W2·W4에서는 둘이 다릅니다 |
| `nextChallenge.ctaLabel` | 그대로 버튼 문구로 사용 |

### `POST /challenges/{id}/report/share-card` → **201** — 공유 카드

```json
// 요청 (본문 자체를 생략해도 됩니다)
{ "mentions": [], "format": "STORY" }
```

- `mentions`가 **빈 배열이면 지목 영역이 안 나옵니다** — 조용한 공유가 기본값입니다. 4명 이상 보내면 앞의 3명만 씁니다.
- `format`은 현재 사용되지 않습니다.

```json
// 응답 201 — 실제 응답 키는 이 6개가 전부입니다
{ "mocked": true, "width": 1080, "height": 1920,
  "deepLink": "https://mildang.app/c/ABC123",
  "hashtag": "#밀가루흥정챌린지",
  "expiresAt": "2026-09-14T..." }
```

- ⚠ **데모에서는 `imageUrl` 키 자체가 없습니다**(서버 렌더 미구현). 클라이언트 캡처로 대체하세요.
- `deepLink`의 마지막 6자리가 `GET /invites/{code}`의 코드입니다.
- `expiresAt`은 30일 뒤입니다.

### `GET /invites/{code}` → 200 — 딥링크 랜딩 (인증 불필요)

```json
{ "inviterNickname": "심사위원3", "period": "W1", "finding": "...", "ctaLabel": "도전 받기" }
```

- 로그인 전에 열리는 화면이라 인증이 필요 없습니다.
- `finding`은 `null`일 수 있습니다. 초대자 닉네임이 없으면 `"친구"`로 대체됩니다.
- 코드는 6자리(헷갈리는 글자 제외한 영숫자)입니다.

---

## 11. 기타

### `GET /health` → 200 (인증 불필요)

```json
{ "status": "ok" }
```

### 데모 전용 (`/demo/*`) — `local`·`demo` 프로필에서만, prod는 404

| 엔드포인트 | 인증 | 용도 |
|-----------|------|------|
| `GET /demo/ping` | ✕ | `{"mocked":true,"status":"pong"}` — 데모 모드 확인 |
| `POST /demo/seed` | ✅ | `{"scenario": "..."}` → `{mocked, scenario, challengeId, balance}` |
| `POST /demo/reset` | ✅ | **204** — 계정 데이터 전체 삭제 |
| `POST /demo/advance-day` | ✅ | `{"days": 7}`(기본 1) — 모든 시각을 N일 **과거로** 이동 |
| `POST /demo/run-batch` | ✅ | `{"jobs": [...]}` → `{mocked, converted, expired, canceled, closed}` |

- **시드 시나리오**: `FRESH` · `DAY4_ACTIVE` · `COMPLETED` · `W2_DAY8` · `W4_DAY12` · `LOW_BALANCE` · `EXPIRED_CONFIRM`
  (시드는 매번 기존 데이터를 지우고 시작합니다. `FRESH`는 `challengeId`·`balance`가 `null`)
- **배치 잡**: `PREPAID_CONVERT` · `ITEM_EXPIRY` · `CHALLENGE_CLOSE`

**심사용 계정** (`idToken`이 곧 계정 키):

| idToken | 상태 |
|---------|------|
| `demo-judge-01` | 신규 (온보딩 시연) |
| `demo-judge-02` | W1 4일차 · 잔액 52 · 선차감 70 |
| `demo-judge-03` | W1 완주 (리포트 시연) |
| `demo-judge-04` | W4 12일차 |
| `demo-judge-05` | W2 (결제 플로우 시연) |

---

## 12. 프론트 체크리스트

- [ ] 항목 가격은 **`effective.points`**만 사용 (`original.points` 아님)
- [ ] `record`·`prepay` 응답의 `budget`으로 잔액 갱신 — 대시보드 재호출 불필요
- [ ] `alreadyProcessed: true`면 성공 토스트 생략
- [ ] 예산 카드는 **`totalBudget`** 표시 (주간값 `budget` 아님)
- [ ] 스캔 행은 `menus[].item`이 있으면 그 값으로 그리기
- [ ] 흥정 종료 버튼 문구는 `closeButtonLabel` 그대로
- [ ] 에러는 `error.message`를 그대로 노출
- [ ] 메뉴 식별 실패는 **422 에러 응답**이고 후보는 `error.detail.candidates`
- [ ] `challenge.label`·`report.challenge.label`에 일차·완주 문구가 이미 포함 (중복 표기 금지)
- [ ] 완주 대비 `challengeId` 로컬 보관 (`current`가 404가 되므로)
- [ ] 카메라 촬영은 canvas → JPEG blob (HEIC 회피). 카메라는 HTTPS에서만 동작
- [ ] 스캔은 20초 이상 걸릴 수 있으므로 로딩 UX 필수
- [ ] 429 재시도 로직 불필요 (레이트 리밋 미구현)
- [ ] **입력 → 항목 생성 흐름에 중복 제출 가드** — 한글 IME는 조합 중 Enter에서 `keydown`이 두 번 뜹니다. `e.nativeEvent.isComposing` 확인 + 진행 중 플래그. (서버가 3초 창으로 병합해주지만 안전망일 뿐입니다)
