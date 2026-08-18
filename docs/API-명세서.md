# 밀당 API 명세서

> **이 파일이 API 계약의 정본입니다.** 코드에서 실제로 구현된 것만 적혀 있습니다.
> 백엔드가 API를 바꾸면 같은 커밋에서 이 파일을 갱신하고, 노션 명세서는 이 파일을 붙여넣어 갱신합니다.
> 기준 코드: `inha-likelion-team2/mildang-server` main · 엔드포인트 34개

> **DB 스키마 변경분은 `docs/DB-변경-v3.1.md`** — v3.0 이후 추가된 테이블·컬럼·열거값을 마이그레이션 DDL과 함께 정리했습니다.

## 빠른 시작 (프론트)

**배포 주소**

```
https://mildang-server-production.up.railway.app/v1
```

CORS는 전 오리진 열려 있어서 Vercel 프리뷰 주소가 배포마다 바뀌어도 그대로 됩니다. `localhost:5173`·`127.0.0.1:5173`도 포함이고 `Authorization` 헤더도 허용돼 있으니, **오리진을 따로 등록해 달라고 요청하실 필요가 없습니다.**

```
NEXT_PUBLIC_API_BASE=https://mildang-server-production.up.railway.app/v1
```

**프론트가 알아야 할 건 두 가지** — 위 주소, 그리고 로그인으로 받은 토큰을 `Authorization: Bearer …`로 붙이는 것.

```ts
const BASE = process.env.NEXT_PUBLIC_API_BASE!;

export async function api(path: string, init: RequestInit = {}) {
  const token = localStorage.getItem("mildang.token");
  const res = await fetch(BASE + path, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  });

  // 성공은 봉투 없이 데이터 그대로, 실패는 { error: { code, message } }
  const body = res.status === 204 ? null : await res.json();
  if (!res.ok) {
    // message는 사용자에게 그대로 보여줘도 되는 한국어입니다
    throw Object.assign(new Error(body?.error?.message ?? "요청에 실패했어요"), {
      status: res.status,
      code: body?.error?.code,
      detail: body?.error?.detail,
    });
  }
  return body;
}
```

**로그인 → 토큰 저장**

```ts
const r = await api("/auth/social", {
  method: "POST",
  body: JSON.stringify({ provider: "KAKAO", idToken: "judge-01", deviceId: "web-1" }),
});
localStorage.setItem("mildang.token", r.accessToken);
```

> ⚠ `idToken`에 아무 문자열이나 넣으면 그게 곧 계정이 됩니다 — **demo 배포에서만** 되는 지름길입니다.
> 실제 카카오 로그인은 §2의 `/auth/kakao` 흐름을 쓰세요.

**시연 상태 만들기** — 4일차·잔액 52·체중 4일치가 들어간 화면을 한 번에 만듭니다.

```ts
await api("/demo/seed", { method: "POST", body: JSON.stringify({ scenario: "DAY4_ACTIVE" }) });
```

`FRESH` `DAY4_ACTIVE` `W2_DAY8` `W4_DAY12` `LOW_BALANCE` `EXPIRED_CONFIRM` `COMPLETED` 중 고르면 됩니다.

**첫 화면 하나 그려보기**

> 갓 만든 계정은 진행 중인 챌린지가 없어서 `/challenges/current`가 **404**입니다. 버그가 아니라 «온보딩으로 보내라»는 신호입니다. 바로 화면을 보고 싶으면 위의 `/demo/seed`를 먼저 부르세요.

```ts
const d = await api("/challenges/current");
d.budget.balance;        // 52
d.budget.mealsLeft;      // 3     → 「잔액 52 · 앞으로 3끼」
d.progress.days;         // 7칸   → 체크박스 (future면 비활성)
d.todayNotice.text;      // 「오늘 잡힌 약속은 없어요」
d.tip?.text;             // 밀당이 말풍선 (없으면 영역 숨기기)
```

**막히면 볼 곳**

- 화면별로 어떤 API를 부르는지 → §3~§9
- 흔한 함정 12개 → **§12 프론트 체크리스트**
- 눌러볼 수 있는 참고 화면 → `/v1/app/index.html` (백엔드가 만든 테스트용 화면. 같은 API를 씁니다)

---

## 개정 이력

| 날짜 | 변경 |
|------|------|
| 2026-08-19 | **설문 `portion` → `amount` 개명**(옛 이름도 계속 받음) + **`survey.weightKg` 추가**. 이름이 어긋나 FE가 보낸 `amount`가 조용히 버려지고 있었다 — 많이 먹는 사람도 전부 `NORMAL`로 계산됨. 체중은 60kg 기준 ±15%로 추정에 반영. `situation`은 계속 예산 미반영(팀 결정 유지). `POST /budget`은 최상위 `weightKg`도 계속 받는다 |
| 2026-08-18 (12) | **CORS preflight(OPTIONS)를 인증에서 제외.** 브라우저는 preflight에 `Authorization`을 싣지 않으므로 토큰을 요구하면 다른 오리진의 FE가 보호된 경로를 아예 못 부른다. 이전에는 500이 나갔다(preflight의 handler가 컨트롤러가 아니라 `PreFlightHandler`라 전역 예외 핸들러가 못 잡음). **본 요청의 인증은 그대로** |
| 2026-08-18 (11) | DB 테이블 **명명 규칙 확정** — 문서를 코드에 맞춘다(단수형이 정본). AI·프론트에는 영향 없음(테이블명이 API·AI 계약에 새지 않음). 대응표는 `docs/DB-변경-v3.1.md` §0 |
| 2026-08-18 (10) | 데모 `POST /demo/advance-day`가 **체중도 함께 이동**시킨다 — 안 옮기면 챌린지만 과거로 가고 체중은 제자리라 진행률 카드의 일차별 체중이 어긋났다 |
| 2026-08-18 (9) | `GET /checkins/today`에 **`lastWeightKg`** 추가 — 오늘 아직 안 쟀을 때 체중 스테퍼가 출발할 값(가장 최근 기록). ⚠ 이 값을 그대로 저장하면 «어제 체중이 오늘 기록»이 되므로, 사용자가 실제로 조작했을 때만 `weightKg`를 보낼 것 |
| 2026-08-18 (8) | **실결제(토스페이먼츠) 연동** — `GET /payments/config`(클라이언트 키), `POST /payments/confirm`{period,paymentKey,orderId,amount}. **금액은 서버가 요금제로 다시 계산해 대조**한다. 키가 없으면 결제 경로가 닫히고 데모 결제(`MOCK`)는 그대로. ⚠ 웹앱이라 IAP가 아니라 **PG**가 맞다 — `PROD_PROVIDERS`를 `TOSS`로 정정 |
| 2026-08-18 (7) | W4 `subtitle`을 **「밀당 대화 무제한 · 가장 긴 한 판」**으로 — 옛 문구는 폐지된 주차별 예산을 가리켜 사실과 달랐다 (Q6 종결) |
| 2026-08-18 (6) | 카카오 웹 로그인 **실계정 확인 완료**. ⚠ 배포 시 `KAKAO_CLIENT_SECRET` 필수 — 새 콘솔은 REST API 키에 클라이언트 시크릿이 **기본 활성화**돼 있어 없으면 토큰 교환이 401로 막힌다 |
| 2026-08-18 (5) | **밀당 대화 횟수 제한 구현** — 결제 화면의 「AI 밀당 대화 40회」. **대화 1번 = 1회**(같은 항목 재흥정은 안 셈), 1주 20·2주 40·4주 무제한. 소진 시 `POST /haggles` → **409 `HAGGLE_QUOTA_EXCEEDED`**. `current.haggleQuota{limit,used,remaining,unlimited}` 신설 |
| 2026-08-18 (4) | 리포트 「내 몸의 변화」 첫 칸을 **칼로리 → 체중 변화**로 (팀 결정). 챌린지 첫 기록과 마지막 기록을 비교해 「58kg → 54kg」·「4.0kg 줄었어요」 |
| 2026-08-18 (3) | **카카오 웹 로그인 신설** — `GET /auth/kakao/authorize-url?redirectUri=` (인가 화면 주소), `POST /auth/kakao` {code, redirectUri, deviceId} (인가 코드 → 서버가 토큰 교환 → id_token 검증 → 우리 토큰). 검증기는 **프로필이 아니라 앱 키 유무**로 갈린다 |
| 2026-08-18 (2) | **리포트에 `completion` 신설** — 확정 와이어프레임 231:1237의 완주 카드를 그대로 그릴 수 있게 문구까지 만들어 보낸다(기간·헤드라인·사용률·요약 + 「내 몸의 변화」 4칸). ⚠ 칼로리 칸은 재료가 없어 `value: null` |
| 2026-08-18 | 필수 쿼리/폼 파라미터 누락이 **500이 아니라 400**으로 나간다(예: `POST /scans`에 `challengeId` 빠짐 → `VALIDATION_FAILED`, `field: challengeId`). 체크인 문항 문구를 확정 와이어프레임 173:813에 맞춤 — 「붓기/얼굴 붓기 변화・체형 변화」·「식곤증/식후 졸림 정도」 |
| 2026-08-17 (14) | `GET /plans`의 `title`을 확정 와이어프레임 문구로 — 「맛보기 한 판/제대로 한 판/장기전」 → **「맛보기/단기/장기」** |
| 2026-08-17 (13) | **`KAKAO_APP_KEY`에 앱 키를 콤마로 여러 개** 넣을 수 있다 — 웹은 로그인 방식에 따라 `id_token.aud`가 JavaScript 키/REST API 키로 갈린다. 하나라도 맞으면 통과 (계약 무변경, 설정만) |
| 2026-08-17 (12) | **확정 세트 렌더 대조 반영** — ① `POST /challenges/{id}/budget`에 **`weightKg`**(설문의 「체중은 어떻게 되나요?」, 선택) ② `POST /items`의 약속을 **날짜(`promiseDate`)로** 받는다(기존 `weekday`도 계속 동작) ③ `GET /analyses/recent`가 **값까지, 4개** |
| 2026-08-17 (11) | **「자주 먹는 것」 이력 집계(§6.7) 구현** — `GET /presets`가 최근 4주 기록을 빈도순으로 주고 `source`가 `HISTORY`가 된다. 이력 칩의 `id`는 `pst_hist_{itemId}`. **AI 게이트에 기준 수량 확인(§8 #12) 추가** — `unit`·`basis` 중 어디에도 수량 표현이 없으면 재분석 |
| 2026-08-17 (10) | **`budget.mealsLeft` 신설** — 화면 「잔액 52 ・앞으로 4끼」. `progress.days[]`에 `weighed` 추가(진행률 카드가 일차별 체중을 같이 그린다) |
| 2026-08-17 (9) | **`current.todayNotice` 신설** — 대시보드 「오늘의 알림」 카드는 **오늘 잡혀 있는 약속**이다(AI 팁과 다른 자리). 오른쪽 「미리 약속을 잡았나요?」는 3a로 가는 **화면 이동뿐**이라 API 없음 |
| 2026-08-17 (8) | **확정 와이어프레임 갱신 반영** — `current`에 `progress` 신설(진행률 체크박스), `GET /items?date=` 정렬을 **「최근 입력한 순」**으로 바꾸고 `weights`·`progress` 동봉 |
| 2026-08-17 (7) | **카카오 로그인 실검증** — prod에서 `idToken`을 카카오 JWKS로 검증한다(서명·`iss`·`aud`·`exp`). 위조·타 앱·타 발급자 토큰은 `TOKEN_INVALID`, 만료는 `TOKEN_EXPIRED`. **demo/local은 기존대로 통과**(문자열이 곧 계정) |
| 2026-08-17 (6) | **기록 보기 화면** — `GET /items`에 `date` 필터 추가(주면 그날치만 + `day{date,count,totalPoints}`), 캘린더용 **`GET /items/dates` 신설**(그 달에 기록이 있는 날) |
| 2026-08-17 (5) | **체중 기록 신설** — 컨디션 체크인에서 함께 받는다(`checkins/today`의 `weightKg`, 선택). — `GET /weights` · `PUT /weights/today`, `current.weights[]`(대시보드 그래프). 값만 저장하고 예산·잔액·리포트에는 관여하지 않는다 |
| 2026-08-17 (4) | **`POST /scans/{id}/menus/{menuId}/comment` 신설** — 화면 4b에서 하단 메뉴를 탭하면 상단 메모가 그 메뉴로 바뀐다. 메뉴마다 코멘트를 따로 만든다 |
| 2026-08-17 (3) | **설문 2문항 추가** — `survey.portion`(한 번 먹는 양, 예산에 0.7/1.0/1.3배) · `survey.situation`(가장 많이 먹는 상황, **예산엔 영향 없음** · AI용 저장). 둘 다 선택 |
| 2026-08-17 (2) | **예산 화면을 확정 피그마(온보딩_03)에 맞춤** — estimate 응답에 `slider{min,max,step,recommended}` 신설, `POST /budget`이 **제안값 3개가 아니라 범위 안의 아무 값이나** 받고 `optionKey`는 선택으로, **`PATCH /challenges/{id}/budget` 신설**(나중에 조정) |
| 2026-08-17 | **공유 딥링크 실동작** — `GET /c/{code}` 랜딩 페이지 신설(무인증), `deepLink`가 `{publicBaseUrl}/v1/c/{code}`로 변경(기존 주소는 받아줄 라우트가 없어 404였다). 흥정 제안에 **하한**(`original/3`) 추가 |
| 2026-08-16 (2) | **배포 전 감사 반영** — ① 흥정 `close`는 확정된 항목에 합의를 반영하지 않는다 ② `POST /challenges`가 온보딩 중 챌린지를 이어서 반환 ③ 리포트 `TOTAL_SPENT`에 선차감 포함·`VS_BUDGET`이 대시보드 잔액과 같은 부호 ④ share-card `mocked`는 demo에서만·딥링크가 배포 도메인 ⑤ 선차감 전환분이 약속 요일에 귀속 ⑥ `/health`가 DB 확인 ⑦ 스캔 가격 수정 후 재담기 시 수정값 반영 |
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
    { "period": "W1", "title": "맛보기", "subtitle": "최초 1회 무료 · 처음이라면 추천",
      "priceKrw": 0, "recommended": true, "available": true, "unavailableReason": null },
    { "period": "W2", "title": "단기", "subtitle": "리포트가 뚜렷해지는 최소 기간",
      "priceKrw": 2000, "recommended": false, "available": true, "unavailableReason": null },
    { "period": "W4", "title": "장기", "subtitle": "밀당 대화 무제한 · 가장 긴 한 판",
      "priceKrw": 3500, "recommended": false, "available": true, "unavailableReason": null }
  ],
  "notice": "결제는 프리미엄이 아니라, 진짜 할 건지 확인하는 문턱이에요."
}
```

- ⚠ `title`은 **"1주"가 아니라 "맛보기"·"단기"·"장기"** 입니다(확정 와이어프레임 85:804 문구). 기간 표기(1주/2주/4주)가 필요하면 `period`로 프론트에서 매핑하세요.
- W4의 `subtitle`은 **「밀당 대화 무제한 · 가장 긴 한 판」**입니다. 예전 문구(「주차별로 예산을 나눠 드려요」)는 v1.3에서 폐지된 주차별 예산을 가리켜 사실과 달랐습니다 (2026-08-18 교체). ⚠ **피그마 85:804에는 옛 문구가 남아 있습니다** — 디자인 쪽 수정이 필요합니다.
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
{ "survey": {
    "noodle": "2-3", "bread": "0-1", "snack": "4+",
    "amount": "NORMAL",
    "situation": "SNACK",
    "weightKg": 65.5
} }
```

화면 «온보딩_03»의 설문입니다.

| 필드 | 문항 | 값 | 예산 반영 |
|------|------|-----|----------|
| `noodle`·`bread`·`snack` | 평소 일주일에 얼마나 자주 드세요? | **`"0-1"` `"2-3"` `"4+"`** (필수) | 기본 추정치 |
| `amount` | 한 번 먹을 때 양은? | `SMALL`(조금) `NORMAL`(보통) `LARGE`(많이) | **×0.7 / ×1.0 / ×1.3** |
| `situation` | 가장 많이 먹는 상황은? | `MEAL` `SNACK` `LATE_NIGHT` `IRREGULAR` | **없음 (전부 ×1.0)** |
| `weightKg` | 체중은 어떻게 되나요? | 20.0 ~ 300.0 | **60kg 기준 ±15% 보정** |

- 빈도 3항목만 필수입니다. 나머지는 선택이고, `amount`를 생략하면 `NORMAL`로 봅니다.
- **`amount`는 예전에 `portion`이었습니다.** 옛 이름도 계속 받으므로 이미 붙인 코드는 그대로 두셔도 됩니다. 새로 쓰신다면 `amount`를 쓰세요.
- **체중 보정**: `m = clamp(1 + (weightKg − 60)/60 × 0.3, 0.85, 1.15)`. 50kg → ×0.95 · 65.5kg → ×1.03 · 90kg → ×1.15. 안 보내면 ×1.0이라 결과가 안 바뀝니다. 몸이 크면 같은 «1인분»도 실제 양이 크다는 것 이상은 주장하지 않으려고 기울기를 얕게 두고 한계를 박았습니다 — 보정이 빈도 설문을 뒤집으면 안 되니까요.
- ⚠ `situation`은 **지금도 예산을 바꾸지 않습니다** (팀 결정 2026-08-17, 2026-08-19 유지 재확인). 저장은 하고 나중에 AI(흥정 어조·팁·리포트)가 씁니다. 화면에서는 받아주세요.
- 빈도 3항목은 필수이고, 다른 문자열은 400입니다.

> ⚠ **추정값이 노션 예시(주 100)와 다릅니다.** 면 2-3 · 빵 0-1 · 간식 4+ 는 주 **265**로 추정됩니다. 노션 §3.3의 100은 라면 한 번(80)에 주 예산이 끝나 챌린지가 성립하지 않아 팀 결정(2026-08-14)으로 상향한 값입니다. 화면에 숫자를 하드코딩하지 마시고 응답값을 그대로 쓰세요.

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
- **`slider`가 화면 «온보딩_03」의 예산 슬라이더(가볍게 ↔ 넉넉하게)를 그립니다.** `min`~`max` 사이에서 `step` 단위로 움직이고, 손잡이 초기 위치는 `recommended`입니다. 범위는 설문 추정치 기준(0.3배~1.6배)이라 추천값이 트랙 가운데쯤 옵니다.
- `options`(HARD·AS_IS·EASY 3장)는 **하위 호환으로 남겨둔 값**입니다. 슬라이더를 쓰면 안 봐도 됩니다.
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

- **`budget`은 주간값**입니다. 서버가 곱수를 적용해 총액으로 저장합니다.
- **슬라이더가 만들 수 있는 값이면 무엇이든 받습니다** — `slider.min`~`slider.max` 사이의 `step` 배수. 범위 밖이거나 단위가 어긋나면 400이고, 메시지에 허용 범위가 들어갑니다.
- **`optionKey`는 선택입니다.** 안 보내면 서버가 값에서 가장 가까운 옵션을 기록합니다(리포트의 재대결 추천에 쓰입니다).
- **`survey`도 선택입니다.** 생략하면 이전 챌린지 설문을 재사용합니다(첫 챌린지에서 생략하면 400).

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

### `PATCH /challenges/{id}/budget` → 200 — 예산 조정

화면 «온보딩_03»의 **"나중에도 언제든지 조정할 수 있어요"**.

```json
// 요청 — 주간값
{ "budget": 200 }
```

응답은 `POST /budget`과 같은 구조입니다(`startTip`은 `null`).

- **진행 중(`ACTIVE`) 챌린지만** 조정할 수 있습니다. 그 외에는 400.
- 범위·단위 규칙은 확정 때와 같습니다(`slider.min`~`max`, `step` 배수).
- **이미 쓴 것은 건드리지 않습니다.** `spent`·`prepaid`는 그대로 두고 총액만 다시 잡으며, 항등식 `balance = total − spent − prepaid`를 유지합니다.
- **이미 쓴 것보다 낮게 내려도 막지 않습니다** — 잔액이 음수가 될 뿐이고, 초과는 항상 허용입니다(§1-4).

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
                        "points": 40, "question": "8월 14일에 40으로 합의한 라면, 드셨어요?" } ],
  "weights": [ { "date": "2026-08-15", "dayIndex": 1, "weightKg": 58.0 } ],
  "progress": { "dayIndex": 4, "totalDays": 7,
                "days": [ { "dayIndex": 1, "date": "2026-08-14",
                            "checkin": true, "recorded": true, "future": false } ] },
  "todayNotice": { "date": "2026-08-17", "text": "오늘 치킨 약속이 있어요",
                   "promises": [ { "id": "itm_...", "name": "치킨", "points": 70,
                                   "weekday": "MON", "prepaid": false } ] }
}
```

| 필드 | 규칙 |
|------|------|
| `challenge.label` | **일차 문구가 이미 포함**돼 있습니다(`1주 챌린지 · 4일차`). 프론트에서 또 붙이지 마세요 |
| `budget.balance` | **선차감이 이미 반영된 값**입니다. `prepaid`를 다시 빼면 안 됩니다 |
| `budget.gaugePercent` | 0~100. 잔액이 음수여도 0으로 클램프 |
| `budget.mealsLeft` | 화면 「잔액 52 ・**앞으로 4끼**」. `totalDays − dayIndex`(최소 1) — 스캔이 AI에 보내는 값과 **같은 계산**입니다 |
| `pace.state` | `AHEAD`/`ON_TRACK`/`BEHIND` — 부호로만 판정(임계 없음) |
| `pace.note` | 그대로 노출 가능한 문구 |
| `weekly` | **항상 `null`입니다** (총액 단일 모델이라 주차 개념이 없음) |
| `tip` | AI 생성, 일 1회. **생성 실패 시 `null`** → 영역을 숨기세요 |
| `today` | **오늘 먹은 것** — 아래 참조 |
| `weights` | 체중 그래프 재료. 기록한 날만 날짜순. 없으면 `[]` |
| `progress` | 화면 「1주 챌린지 진행률」의 **체크박스 N칸**. `days`는 시작일부터 `totalDays`만큼 **빠짐없이** 옵니다 |
| `progress.days[].weighed` | 그날 체중을 남겼는지. 진행률 카드가 일차 아래에 체중을 같이 그려서 함께 줍니다(값 자체는 `weights[]`에) |
| `progress.days[].checkin` / `.recorded` | 그날 **컨디션 체크인을 했는지** / **기록을 남겼는지**. 체크박스를 어느 쪽으로 칠할지는 화면이 정하세요 — 그래서 둘 다 줍니다 |
| `progress.days[].future` | 아직 오지 않은 날. 체크박스를 비워두면 됩니다 |
| `haggleQuota` | 이번 판에 남은 **밀당 대화 횟수**. `unlimited: true`면 `limit`·`remaining`이 없습니다(4주). 벽에 부딪히기 전에 화면이 미리 보여줄 수 있게 대시보드에 함께 실립니다 |
| `todayNotice` | 화면 「오늘의 알림」 카드 = **오늘 잡혀 있는 약속**입니다. ⚠ 밀당이 말풍선의 AI 팁(`tip`)과 **다른 자리**입니다 |
| `todayNotice.text` | 그대로 노출 가능한 문구 — 0건 「오늘 잡힌 약속은 없어요」 · 1건 「오늘 치킨 약속이 있어요」 · 2건 이상 「오늘 약속이 2건 있어요」 |
| `todayNotice.promises` | 오늘 요일로 잡힌 약속(`PENDING`·`HAGGLED`·`PREPAID`). **약속이 없어도 카드는 남습니다** — `promises: []`, `todayNotice`는 `null`이 되지 않습니다 |
| `todayNotice.promises[].prepaid` | 이미 예산에서 미리 빼둔 약속인지. 선차감해도 카드에서 사라지지 않습니다 |
| 「미리 약속을 잡았나요?」 버튼 | 3a 약속 사전 결제로 가는 **화면 이동뿐** — 호출할 API가 없습니다 |
| `prepaidItems[]` | 선차감된 약속 카드 (전체) |
| `checkin.doneToday` | `false`면 체크인 버튼에 뱃지. `dueAt`은 오늘 22:00 KST |
| `weights[]` | 체중 기록 그래프 재료 — `{date, dayIndex, weightKg}`, 날짜 오름차순. 기록이 없으면 `[]` |
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

### `GET /items?kind=&status=&limit=&date=` → 200

| 파라미터 | 기본값 | 비고 |
|---------|--------|------|
| `kind` | 전체 | `MEAL`·`PROMISE` |
| `status` | `PENDING,HAGGLED` | 콤마 구분 다중 지정. 잘못된 값이면 400 |
| `limit` | 20 | 서버에서 **최대 50**으로 자름 |
| `date` | 없음(전체 기간) | `2026-08-16`. **주면 그날 것만** — 화면 「기록 보기」용. 형식이 틀리면 400 |

```json
{ "items": [ /* 항목 객체 */ ],
  "summary": { "count": 3, "totalPoints": 55, "balanceAfterAll": 170 },
  "day": { "date": "2026-08-16", "count": 2, "totalPoints": 125 },  // 이하 date를 줬을 때만
  "weights": [ /* current.weights 와 동일 */ ],
  "progress": { /* current.progress 와 동일 */ }
}
```

- ⚠ **`summary`는 쿼리 필터와 무관하게 미기록 전체(`PENDING`·`HAGGLED`·`EXPIRED`) 고정값**입니다. 필터를 걸어도 값이 안 바뀝니다. 화면 상단의 「총 5건」은 `summary`가 아니라 **`day`** 를 쓰세요.
- **`day`·`weights`·`progress`는 `date`를 줬을 때만** 응답에 들어갑니다(안 주면 키 자체가 없음). `day.totalPoints`는 그날 항목들의 `effective.points` 합입니다.
- **`weights`·`progress`는 대시보드(`GET /challenges/current`)의 같은 필드와 같은 값**입니다. 「기록 보기」 화면이 목록·체중 그래프·진행률을 한 화면에 그려서 **한 번의 호출로 끝나게** 함께 실어 보냅니다.
- 기록이 없는 날도 **200**입니다 — `items: []`, `day.count: 0` (404 아님).
- 정렬: 기본은 `createdAt` 내림차순(최신 먼저). **`date`를 줘도 최신 먼저**입니다 — 화면 라벨 「최근 입력한 순」에 맞춘 것으로, 확정 전 항목은 `recordedAt`이 없어 `createdAt`으로 대신합니다.
- 날짜 기준은 **05:00 KST 경계**(`logicalDate`)입니다. 새벽 3시에 먹은 라면은 **전날** 목록에 들어갑니다.

### `GET /items/dates?month=` → 200

캘린더에서 **기록이 있는 날에 표시**를 하기 위한 목록입니다.

| 파라미터 | 기본값 | 비고 |
|---------|--------|------|
| `month` | 이번 달 | `2026-08`. 형식이 틀리면 400 |

```json
{ "month": "2026-08",
  "days": [ { "date": "2026-08-16", "count": 2, "totalPoints": 125 },
            { "date": "2026-08-17", "count": 1, "totalPoints": 80 } ] }
```

- **기록이 있는 날만** 담깁니다 — 없는 날은 아예 안 들어옵니다(0으로 채워 보내지 않음). 캘린더는 이 목록에 있는 날짜에만 점을 찍으면 됩니다.
- `RECORDED`만 셉니다. 확정 전(`PENDING`·`HAGGLED`)이나 만료는 «먹은 날»이 아니라서 제외합니다.
- 날짜를 하나 고르면 `GET /items?date=...&status=RECORDED`로 그날 목록을 받으세요.
- 범위는 **현재 진행 중인 챌린지**입니다 — 지난 챌린지 기록은 아직 조회 대상이 아닙니다(미정 Q4).

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
> **직접 입력·프리셋 소스에 한해**, 같은 메뉴·같은 값·같은 종류(+약속은 같은 요일)의 확정 전 항목이 3초 안에 이미 만들어졌으면 새로 만들지 않고 그 항목을 반환합니다. (스캔은 위 (a) 규칙이 정확히 처리하므로 제외 — 메뉴판에 같은 이름이 두 줄 있어도 서로 다른 칸으로 취급합니다.)
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

- **인증이 필요합니다** — 사람마다 다른 목록이 나갑니다.
- `source`는 이력에서 하나라도 뽑혔으면 **`HISTORY`**, 아니면 **`DEFAULT`**(처음 쓰는 사람)입니다.
- **최근 4주에 `RECORDED`한 항목**을 메뉴명으로 묶어 **빈도순**(같으면 최근 순)으로 최대 4개. 챌린지를 넘어 **사용자 단위**로 봅니다 — 지난 판에 자주 먹던 것도 자주 먹는 것입니다.
- 모자라면 기본 4종으로 채웁니다. **같은 메뉴가 두 번 오지 않습니다.**
- 이력에서 뽑힌 칩의 `id`는 **`pst_hist_{itemId}`** 형태입니다. `POST /items`에 그대로 넣으면 그때 그 항목의 `original`을 복사해 새 항목을 만듭니다(흥정 이력은 물려받지 않습니다). **남의 칩 id를 넣으면 404**입니다.
- ⚠ 표시 가격은 **항상 `original.points`**입니다. 라면 80을 40에 합의해 먹었어도 칩은 **80**입니다. 합의값을 칩에 쓰면 그게 다음 기준선이 되고, 또 흥정하면 20이 되어 판마다 기준선이 무너집니다(합의값은 흥정 오프닝 대사에서만 언급).

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

### `POST /scans/{id}/menus/{menuId}/comment` → 200 — 메뉴 탭 (상단 메모 갱신)

화면 4b에서 **하단 목록의 메뉴를 탭했을 때** 상단 노란 메모를 채웁니다. 요청 본문은 없습니다.

```json
{
  "menuId": "mnu_3",
  "name": "제육볶음",
  "points": 15,
  "basis": "시판 고추장 베이스로 추정",
  "comment": "\"냉면(40)을 고르면 내일이 빠듯해요. 15이면 남는 장사죠.\"",
  "balanceAfter": 37
}
```

| 필드 | 규칙 |
|------|------|
| `comment` | **탭한 메뉴 기준으로 새로 만든 밀당이 문구.** 생성 실패 시 `null` — 그때는 말풍선만 접으세요 |
| `balanceAfter` | 이 메뉴를 기록하면 남을 잔액. 「탭해서 N 차감」 옆에 씁니다 |
| `basis` | 추정 근거 한 줄 |

- **추천 메뉴의 `recommendation.comment`를 다른 메뉴에 그대로 쓰면 안 됩니다.** 그 문구는 특정 메뉴를 비교 대상으로 지목하고 있어서, 그 메뉴를 골랐을 때 앞뒤가 안 맞습니다.
- **한 번 만든 코멘트는 저장됩니다** — 같은 메뉴를 다시 탭하면 AI를 부르지 않고 즉시 같은 문구가 옵니다.
- 가격을 수정(`PATCH`)하면 그 메뉴의 코멘트는 버려지고, 다음 탭에서 새로 만듭니다.
- ⚠ **첫 탭은 AI 호출이라 수 초 걸립니다.** 탭 직후에 이름·가격·`balanceAfter`를 먼저 그리고 말풍선만 나중에 채우는 편이 자연스럽습니다.
- 없는 메뉴면 404.

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
| `proposal.points` | **하한 ≤ points ≤ 원래값** (게이트가 강제). 하한 = `min(원래값, max(1, round(원래값/3)))` — "1포인트로 해줘" 같은 요청에도 그 아래로는 안 내려갑니다 |
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
  "checkinDays": { "answered": 3, "elapsed": 4, "total": 7 },
  "weightKg": null
}
```

- `answers`는 아직 제출 전이면 `null`입니다.
- `checkinDays`는 **원시 수치만** 줍니다(비율·임계 없음). "3/7일 기록" 같은 표기는 프론트 몫입니다.
- `weightKg`는 그날 기록된 체중(없으면 `null`) — 화면을 다시 열었을 때 넣었던 값을 보여주는 용도입니다.

### `PUT /checkins/today` → 200 — 멱등 덮어쓰기

```json
// 요청
{ "answers": { "BLOAT": "BAD", "SKIN": "MID", "DROWSY": "GOOD" },
  "weightKg": 56.2 }
```

- ⚠ **키는 대문자 그대로**입니다(`BLOAT`·`SKIN`·`DROWSY`).
- **세 항목 모두 필수**입니다. 하나라도 빠지면 400.
- **`weightKg`는 선택**입니다(화면의 「건너뛰어도 괜찮아요」). 보내면 같은 날짜의 체중 기록에 함께 남습니다 — `PUT /weights/today`로 넣은 것과 **같은 자리**라 하루 한 건입니다.
- 체중 없이 컨디션만 보내도 **이미 넣어둔 그날 체중은 지워지지 않습니다.**
- 같은 날 다시 보내면 덮어씁니다(에러 아님).

```json
// 응답 200
{ "id": "chk_...", "date": "2026-08-15", "done": true,
  "answers": { "BLOAT": "BAD", "SKIN": "MID", "DROWSY": "GOOD" },
  "message": "접수 완료. 오늘 장부는 닫습니다 — 내일 봬요.",
  "checkinDays": { "answered": 4, "elapsed": 4, "total": 7 } }
```

---

## 9-1. 체중 (`/weights`)

화면 3 대시보드의 «1일차 58kg · 2일차 55kg» 그래프.

> **값만 남깁니다.** 예산·잔액·리포트 통계 어디에도 관여하지 않습니다. 나중에 리포트의 AI 분석이 쓰기 위해 저장해두는 것입니다 (팀 결정 2026-08-17).

### `PUT /weights/today` → 200 — 오늘 체중

```json
// 요청
{ "weightKg": 58.0 }
```

- **하루 한 건**입니다. 같은 날 다시 보내면 덮어씁니다(체크인과 같은 규칙).
- kg, 소수점 한 자리로 반올림합니다. **20.0~300.0** 범위를 벗어나면 400(오타로 봅니다).

```json
// 응답
{
  "today": { "date": "2026-08-17", "dayIndex": 1, "weightKg": 58.0 },
  "series": [ { "date": "2026-08-17", "dayIndex": 1, "weightKg": 58.0 } ]
}
```

### `GET /weights` → 200

같은 구조입니다. 오늘 기록이 없으면 `today`가 `null`이고 `series`만 옵니다.

- 그래프만 그릴 거면 **따로 부르지 않아도 됩니다** — `GET /challenges/current`의 `weights[]`에 같은 시리즈가 들어 있습니다.

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
| `stats[0]` `TOTAL_SPENT` | **선차감(`prepaid`) 포함** — 예산에서 빠져나간 총액 |
| `stats[1]` `VS_BUDGET` | **남은 예산** = 대시보드 `budget.balance`와 같은 값·같은 부호. 음수면 초과 |
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
// 응답 201 — 실제 응답 키는 이 6개가 전부입니다 (prod에서는 mocked가 빠져 5개)
{ "mocked": true, "width": 1080, "height": 1920,
  "deepLink": "https://<배포도메인>/c/ABC123",
  "hashtag": "#밀가루흥정챌린지",
  "expiresAt": "2026-09-14T..." }
```

- ⚠ **데모에서는 `imageUrl` 키 자체가 없습니다**(서버 렌더 미구현). 클라이언트 캡처로 대체하세요.
- `deepLink`는 **브라우저로 열면 실제 랜딩 페이지가 뜨는 주소**입니다(아래 참조). 마지막 6자리가 초대 코드입니다.
- `expiresAt`은 30일 뒤입니다.

### `GET /invites/{code}` → 200 — 딥링크 랜딩 (인증 불필요)

```json
{ "inviterNickname": "심사위원3", "period": "W1", "finding": "...", "ctaLabel": "도전 받기" }
```

- 로그인 전에 열리는 화면이라 인증이 필요 없습니다.
- `finding`은 `null`일 수 있습니다. 초대자 닉네임이 없으면 `"친구"`로 대체됩니다.
- 코드는 6자리(헷갈리는 글자 제외한 영숫자)입니다.

### `GET /c/{code}` — 공유 링크 랜딩 페이지 (인증 불필요)

**공유 카드의 `deepLink`가 가리키는 주소.** JSON이 아니라 **사람이 보는 HTML 페이지**를 돌려줍니다 — 초대자 닉네임, 기간, 발견 문구, 「도전 받기」 버튼(→ 앱). 내부적으로 위의 `GET /invites/{code}`를 호출합니다.

- 코드가 없거나 잘못됐으면 안내 문구 + 「밀당 시작하기」 버튼을 보여줍니다(404를 던지지 않습니다).
- 실 FE가 붙으면 이 페이지를 FE 라우트로 대체하면 됩니다. 그때 `deepLink` 생성 규칙(`ReportService`)도 같이 바꿔야 합니다.

---

## 11. 기타

### `GET /health` → 200 (인증 불필요)

```json
{ "status": "ok", "db": "ok" }
```

**DB 연결까지 실제로 확인합니다.** DB에 닿지 못하면 **503** `{"status":"down","db":"unreachable"}`입니다.

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
- [ ] **메뉴 탭은 두 단계로 그리기** — 이름·가격·`balanceAfter`는 이미 `menus[]`에 있으니 즉시 그리고, `comment`(AI 호출, 수 초)만 나중에 채우세요
- [ ] **입력 → 항목 생성 흐름에 중복 제출 가드** — 한글 IME는 조합 중 Enter에서 `keydown`이 두 번 뜹니다. `e.nativeEvent.isComposing` 확인 + 진행 중 플래그. (서버가 3초 창으로 병합해주지만 안전망일 뿐입니다)

### 2026-08-18 추가분 (확정 와이어프레임 반영)

> **기존 필드는 하나도 지우거나 이름을 바꾸지 않았습니다.** 전부 «추가»라, 이미 만든 화면은 그대로 돕니다.

- [ ] **화면 문구는 서버 값을 그대로 쓰기** — `plans[].title/subtitle`, `checkins.questions[].label/desc`가 확정 시안 문구로 바뀌었습니다. 하드코딩해 두면 옛 문구가 남습니다
- [ ] **체크인 선택지 라벨은 「좋음 / 보통 / 나쁨」** — 값은 그대로 `GOOD`·`MID`·`BAD`입니다
- [ ] **체중 스테퍼는 `lastWeightKg`에서 출발** — `weightKg`(오늘 값)가 `null`이면 `lastWeightKg`로 시작하되, **사용자가 실제로 조작했을 때만** `weightKg`를 보내세요. 안 그러면 어제 체중이 오늘 기록이 됩니다
- [ ] **약속은 날짜로 보내기** — `promiseDate`(`2026-08-20`). 요일은 서버가 뽑습니다. 기존 `weekday`도 계속 받지만 화면은 날짜를 고릅니다
- [ ] **진행률 체크박스는 `!progress.days[].future`** 로 칠하기 (날짜 기준). `checkin`·`recorded`·`weighed`는 참고용입니다
- [ ] **「오늘의 알림」은 `todayNotice`** (= 오늘 잡힌 약속). 밀당이 말풍선의 AI 팁(`tip`)과 **다른 자리**입니다
- [ ] **밀당 대화 횟수** — `haggleQuota.remaining`을 미리 보여주고, 소진 시 `POST /haggles`가 **409 `HAGGLE_QUOTA_EXCEEDED`**로 막힙니다. 그때도 기록·선차감은 됩니다
- [ ] **기록 보기는 캘린더 → 목록** — `GET /items/dates?month=`로 점 찍을 날을 받고, 고른 날짜로 `GET /items?date=`. 목록 정렬은 **최신 입력순**입니다
- [ ] **완주 리포트는 `completion` 하나로** 그려집니다 — 문구까지 서버가 만들어 보냅니다
- [ ] **결제는 두 단계** — `GET /payments/config`로 클라이언트 키를 받고(없으면 `configured:false` → 데모 결제), 결제창 통과 후 `POST /payments/confirm`. ⚠ **금액은 서버가 다시 계산해 대조**하므로 `amount`를 임의로 바꾸면 400입니다
- [ ] **카카오 로그인** — `GET /auth/kakao/authorize-url?redirectUri=`로 인가 주소를 받아 이동하고, 돌아온 `?code=`를 `POST /auth/kakao`로 넘기세요. 앱 키를 프론트에 박지 마세요
- [ ] 필수 파라미터 누락이 이제 **400**입니다(예전엔 500). `error.field`로 어느 값인지 옵니다
