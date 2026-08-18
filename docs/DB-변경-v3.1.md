# 밀당 DB 스키마 v3.0 → v3.1 변경분

> 기준 문서: `밀당 DB 스키마 v3.0`
> 이 문서는 **v3.0 이후 바뀐 것만** 담습니다. 바뀌지 않은 테이블은 v3.0을 그대로 보세요.
> 마지막 갱신: **2026-08-18**
>
> `§`는 별도 표기가 없으면 **API 명세서**의 절 번호입니다.

**v3.0 → v3.1 — 확정 와이어프레임 반영 (마이그레이션 필요)**

| 구분 | 내용 |
| --- | --- |
| **추가 테이블** | `weight_logs` — 체중 기록 (1건) |
| **추가 컬럼** | `challenges.survey_portion` · `challenges.survey_situation` · `scan_menus.comment` · `haggle_sessions.challenge_id` |
| **추가 열거형** | `Portion`(`SMALL` `NORMAL` `LARGE`) · `Situation`(`MEAL` `SNACK` `LATE_NIGHT` `IRREGULAR`) |
| **열거값 변경** | `PayProvider` — `IAP_APPLE` `IAP_GOOGLE` **삭제**, **`TOSS` 추가** (웹앱이라 IAP가 아니라 PG) |
| **에러 코드 추가** | `HAGGLE_QUOTA_EXCEEDED`(409) · `PAYMENT_FAILED`(400) · `PAYMENT_AMOUNT_MISMATCH`(400) |
| **산식 변경** | 예산이 **3옵션 선택 → 슬라이더 범위 내 임의값**. `budget_option_key`는 선택값이 되고 `cut_rate_percent`는 사후 분류용으로만 남음 |
| **⚠ 명명 불일치** | 실제 생성 테이블명이 이 문서의 복수형과 다름 — **DB §0 참조** |

---

## 0. ⚠ 먼저 — 테이블 명명이 문서와 다릅니다

v3.0 문서는 전 테이블을 **복수형**(`challenges` `items` …)으로 적었지만, 현재 코드가 실제로 만드는 이름은 **일부만 복수형**입니다. JPA에서 `@Table(name = …)`을 지정한 것만 복수형이고 나머지는 클래스명 그대로 단수형이 됩니다.

| 문서 (v3.0) | 실제 생성 | 지정 여부 |
| --- | --- | --- |
| `users` | `users` | ✅ 지정됨 |
| `analyses` | `analyses` | ✅ 지정됨 |
| `reports` | `reports` | ✅ 지정됨 |
| `dashboard_tips` | `dashboard_tips` | ✅ 지정됨 |
| `challenges` | **`challenge`** | ❌ 기본 규칙 |
| `items` | **`item`** | ❌ |
| `payments` | **`payment`** | ❌ |
| `checkins` | **`checkin`** | ❌ |
| `scans` | **`scan`** | ❌ |
| `scan_menus` | **`scan_menu`** | ❌ |
| `haggle_sessions` | **`haggle_session`** | ❌ |
| `haggle_messages` | **`haggle_message`** | ❌ |
| `user_sessions` | **`user_session`** | ❌ |
| (신규) `weight_logs` | **`weight_log`** | ❌ |

**동작에는 문제가 없습니다** — Hibernate가 만들고 Hibernate가 검증하므로 자기들끼리는 맞습니다. 하지만 **이 문서를 보고 SQL을 쓰면 "테이블 없음"이 납니다.** 운영 쿼리·마이그레이션 스크립트·백오피스가 전부 걸립니다.

**정하고 가야 합니다. 둘 중 하나입니다.**

1. **코드를 문서에 맞춘다** — 엔티티 10개에 `@Table(name = "…")`를 붙여 복수형으로. 이미 만들어진 demo DB는 테이블 rename이 필요합니다.
2. **문서를 코드에 맞춘다** — v3.1 문서의 테이블명을 단수형으로 고칩니다. 코드 변경은 없습니다.

⚠ **prod는 `ddl-auto: validate`라 이름이 어긋나면 기동 자체가 실패합니다.** 지금 정해두지 않으면 배포 당일에 드러납니다.

> 아래 DDL은 **실제 생성 이름(단수형)** 기준으로 적었습니다. 1번으로 정하면 이름만 바꿔 쓰세요.

---

## 1. 신규 테이블 — `weight_log`

체중 기록. **화면 3**(대시보드 그래프·진행률 카드), **화면 6**(컨디션 체크인), **온보딩_03**(시작 체중), **리포트**의 「내 몸의 변화」가 씁니다.

**값만 저장합니다.** 예산·잔액·페이스 계산에는 일절 관여하지 않습니다 (팀 결정 2026-08-17). 원칙 #5(계산 가능한 값은 저장 안 함)의 예외가 아니라, **측정값이라 계산할 수 없는 것**입니다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `id` | text | PK | `wgt_{ULID}` — §0.7 prefix 목록에 `wgt` 추가 |
| `challenge_id` | text | FK → challenges, NOT NULL | |
| `user_id` | text | FK → users, NOT NULL | |
| `date` | date | NOT NULL, **UNIQUE(challenge_id, date)** | §0.8 경계(05:00 KST) 기준. **`PUT` 멱등의 근거** — UPSERT |
| `day_index` | smallint | NOT NULL | 기록 시점에 확정. 그래프의 X축 |
| `weight_kg` | numeric(4,1) | NOT NULL, CHECK `20.0 ~ 300.0` | 소수 1자리 |
| `created_at` | timestamptz | NOT NULL | |
| `updated_at` | timestamptz | NOT NULL | 재호출 시 덮어쓰기 |

```sql
CREATE TABLE weight_log (
    id           text          NOT NULL PRIMARY KEY,
    challenge_id text          NOT NULL REFERENCES challenge(id),
    user_id      text          NOT NULL REFERENCES users(id),
    date         date          NOT NULL,
    day_index    smallint      NOT NULL,
    weight_kg    numeric(4,1)  NOT NULL CHECK (weight_kg BETWEEN 20.0 AND 300.0),
    created_at   timestamptz   NOT NULL,
    updated_at   timestamptz   NOT NULL,
    CONSTRAINT uk_weight_challenge_date UNIQUE (challenge_id, date)
);
```

- **하루 한 건입니다.** `checkins`와 같은 규칙 — 같은 날 다시 보내면 덮어씁니다.
- **들어오는 경로가 셋인데 자리는 하나입니다** — `POST /challenges/{id}/budget`의 `weightKg`(시작 체중), `PUT /checkins/today`의 `weightKg`, `PUT /weights/today`. 어느 쪽으로 넣든 같은 행에 쌓입니다.
- `day_index`는 v3.0 원칙 #5(파생값 저장 금지)에 어긋나 보이지만, **기록 시점의 일차를 고정**해야 합니다. 데모의 날짜 이동(`/demo/advance-day`)처럼 `started_at`이 움직이면 재계산값이 흔들립니다.

**인덱스**

| 인덱스 | 용도 |
| --- | --- |
| `(challenge_id, date)` (UNIQUE) | 오늘 값 조회 · 그래프 정렬 |

---

## 2. 컬럼 추가

### 2.1 `challenge` — 설문 2문항 (온보딩_03)

v3.0의 설문은 빈도 3문항(`survey_noodle` `survey_bread` `survey_snack`)뿐이었는데, 확정 와이어프레임(141:1157)에 **양**과 **상황**이 추가됐습니다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `survey_portion` | varchar(8) | CHECK `IN ('SMALL','NORMAL','LARGE')` | 한 번 먹는 양. **예산에 0.7 / 1.0 / 1.3배** |
| `survey_situation` | varchar(16) | CHECK `IN ('MEAL','SNACK','LATE_NIGHT','IRREGULAR')` | 가장 많이 먹는 상황. **예산에 영향 없음** |

```sql
ALTER TABLE challenge ADD COLUMN survey_portion   varchar(8);
ALTER TABLE challenge ADD COLUMN survey_situation varchar(16);
ALTER TABLE challenge ADD CONSTRAINT ck_survey_portion
    CHECK (survey_portion IS NULL OR survey_portion IN ('SMALL','NORMAL','LARGE'));
ALTER TABLE challenge ADD CONSTRAINT ck_survey_situation
    CHECK (survey_situation IS NULL OR survey_situation IN ('MEAL','SNACK','LATE_NIGHT','IRREGULAR'));
```

- 둘 다 **nullable**입니다 — 설문에서 건너뛸 수 있습니다.
- **`survey_situation`은 예산 산식에 들어가지 않습니다** (팀 결정 2026-08-17, 전 값 가중치 1.0). 리포트의 AI 분석이 나중에 쓰라고 **값만 남깁니다.** 저장하면서 쓰지 않는 유일한 컬럼이라 의도를 명시해 둡니다.
- `estimated_weekly` 산식이 바뀌었습니다: `round5(빈도 기반 추정 × portion 배수)`.

### 2.2 `scan_menu` — 메뉴별 코멘트 (화면 4b)

확정 와이어프레임(193:1556)에서 **하단 메뉴를 탭할 때마다 상단 노란 카드가 그 메뉴로 바뀝니다.** 메뉴마다 코멘트가 따로 필요합니다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `comment` | text | CHECK length ≤ 90 | 탭 시 생성 후 캐시. `null`이면 아직 안 만든 것 |

```sql
ALTER TABLE scan_menu ADD COLUMN comment text;
ALTER TABLE scan_menu ADD CONSTRAINT ck_scan_menu_comment
    CHECK (comment IS NULL OR length(comment) <= 90);
```

- v3.0의 `scans.recommendation_comment`(스캔당 1건, 백엔드가 고른 추천 메뉴)는 **그대로 남습니다.** 이 컬럼은 **사용자가 탭한 메뉴**의 코멘트라 자리가 다릅니다.
- ⚠ **가격을 수정하면(`PATCH /scans/{id}/menus/{menuId}`) `null`로 지웁니다.** 옛 값 기준의 코멘트가 남으면 화면이 거짓말을 합니다.

### 2.3 `haggle_session` — 챌린지 귀속

요금제별 **밀당 대화 횟수**를 «이번 판» 기준으로 세기 위해 필요합니다. v3.0에는 `item_id`만 있어서 챌린지를 알려면 `items`를 조인해야 했습니다.

| 컬럼 | 타입 | 제약 | 설명 |
| --- | --- | --- | --- |
| `challenge_id` | text | FK → challenges, NOT NULL | |

```sql
-- 기존 행이 있는 환경(demo)은 채운 뒤 NOT NULL을 건다
ALTER TABLE haggle_session ADD COLUMN challenge_id text;
UPDATE haggle_session s
   SET challenge_id = (SELECT i.challenge_id FROM item i WHERE i.id = s.item_id);
ALTER TABLE haggle_session ALTER COLUMN challenge_id SET NOT NULL;
ALTER TABLE haggle_session ADD CONSTRAINT fk_haggle_challenge
    FOREIGN KEY (challenge_id) REFERENCES challenge(id);
```

**인덱스**

| 인덱스 | 용도 |
| --- | --- |
| `(challenge_id, item_id)` | 대화 횟수 집계 — `COUNT(DISTINCT item_id)` |

> **횟수는 세션 수가 아니라 «항목 수»로 셉니다.** 같은 항목을 다시 여는 재흥정은 마음을 바꾼 것이지 새 대화가 아니라, 세션 수로 세면 고민할 때마다 횟수가 깎입니다.

---

## 3. 열거값 변경

### 3.1 `PayProvider` — IAP → PG

| v3.0 | v3.1 |
| --- | --- |
| `IAP_APPLE` `IAP_GOOGLE` `MOCK` | **`TOSS`** `MOCK` |

⚠ **웹앱이라 인앱결제(IAP)가 아니라 PG가 맞습니다.** v3.0의 전제가 틀렸습니다. 컬럼 타입·길이는 그대로고 들어가는 값만 바뀝니다.

```sql
ALTER TABLE payment DROP CONSTRAINT IF EXISTS ck_payment_provider;
ALTER TABLE payment ADD CONSTRAINT ck_payment_provider
    CHECK (provider IN ('TOSS','MOCK'));
```

- `MOCK`은 v3.0대로 **demo 전용**입니다.
- **`receipt_hash`에는 토스 주문번호(`orderId`)의 SHA-256**을 넣습니다. v3.0의 «영수증 리플레이 차단» 용도 그대로이고, 같은 주문 재요청 시 200 멱등의 조회 키입니다.
- `UNIQUE(provider, receipt_hash)` 제약은 그대로 유효합니다.

### 3.2 신규 열거형

| 논리 타입 | 값 | 사용 컬럼 |
| --- | --- | --- |
| Portion | `SMALL` `NORMAL` `LARGE` | `challenge.survey_portion` |
| Situation | `MEAL` `SNACK` `LATE_NIGHT` `IRREGULAR` | `challenge.survey_situation` |

### 3.3 에러 코드 추가 (테이블 영향 없음)

| 코드 | 상태 | 뜻 |
| --- | --- | --- |
| `HAGGLE_QUOTA_EXCEEDED` | 409 | 이번 판 대화 횟수 소진 (1주 20 · 2주 40 · 4주 무제한) |
| `PAYMENT_FAILED` | 400 | PG 승인 실패 |
| `PAYMENT_AMOUNT_MISMATCH` | 400 | 요청 금액이 요금제 가격과 다름 |

---

## 4. 산식 변경 — 예산

v3.0은 **3옵션 중 택1**(`HARD` `AS_IS` `EASY`)이었는데, 확정 와이어프레임(85:853)이 **슬라이더**로 바뀌었습니다.

| 값 | v3.0 | v3.1 |
| --- | --- | --- |
| 사용자 선택 | `options[key].budget` 중 하나와 **정확히 일치** | `slider.min ~ slider.max` 범위의 **`step` 배수 아무 값** |
| `budget_option_key` | **확정 시 필수** | **선택** — 안 보내면 서버가 가장 가까운 옵션으로 분류 |
| `cut_rate_percent` | 선택한 옵션의 컷률 | 분류된 옵션의 컷률 (사후 기록용) |

**슬라이더 범위** (`estimate` 응답의 `slider`)

| 값 | 계산 |
| --- | --- |
| `min` | `max(30, round5(estimated_weekly × 0.3))` |
| `max` | `min(900, round5(estimated_weekly × 1.6))` |
| `step` | `5` |
| `recommended` | `options['AS_IS'].budget` |

**컬럼 변경은 없습니다.** `budget_weekly` `budget_total` `budget_option_key` `cut_rate_percent` 네 컬럼이 그대로 쓰이고, **v3.0의 CHECK 제약도 그대로 유효**합니다:

```
CHECK (budget_total = budget_weekly * 곱수)
```

다만 **`budget_weekly`가 3개 값 중 하나라는 보장이 사라졌습니다.** v3.0 DB §14의 검증 항목 중 「`budget_weekly`가 `estimated_weekly` + `cut_rate_percent`로 재계산한 값과 일치하는지」는 **더 이상 성립하지 않습니다.** 대신 「슬라이더 범위 안이고 `step` 배수인지」로 바꿔야 합니다.

**추가 엔드포인트** — `PATCH /challenges/{id}/budget`(진행 중 조정). 화면의 「나중에도 언제든지 조정할 수 있어요」. `spent`·`prepaid`는 건드리지 않고 `budget_total`만 다시 잡으며, **항등식은 그대로 성립**합니다(새 총액이 이미 쓴 것보다 적으면 잔액이 음수 → 초과로 정상 처리).

---

## 5. §0.7 ID prefix 추가

| prefix | 대상 |
| --- | --- |
| `wgt` | `weight_log.id` |

v3.0의 목록(`usr` `pay` `chl` `anl` `scn` `itm` `hgl` `chk` `tip`)에 하나 추가됩니다.

---

## 6. 반영 순서 (prod 배포 시)

1. **DB §0의 명명 문제를 먼저 정합니다.** 안 정하면 아래 DDL의 테이블명이 틀립니다.
2. DDL 적용 — §1(신규 테이블) → §2(컬럼) → §3.1(CHECK 교체) 순서. `haggle_session`은 UPDATE를 끼워야 합니다.
3. 애플리케이션 배포
4. 기동 확인 — `prod`는 `ddl-auto: validate`라 **누락이 있으면 기동 자체가 실패**하므로 즉시 드러납니다.

---

## 7. 바뀌지 않은 것

`users` · `user_session` · `analyses` · `scan` · `item` · `haggle_message` · `checkin` · `dashboard_tips` · `reports` 는 **컬럼 변경이 없습니다.**

특히 `item`에 컬럼을 더하지 않았습니다 — 확정 화면(193:1295)이 약속을 **날짜**로 받지만(`promiseDate`), 서버가 요일을 뽑아 기존 `weekday` 컬럼에 넣습니다. **저장 모델을 바꿀 이유가 없어서** 화면 입력 방식만 바뀐 것으로 처리했습니다.

`reports`도 컬럼이 그대로입니다 — 확정 리포트(231:1237)의 완주 카드(`completion`)는 **전부 조회 시 조립**합니다. 사용률·「내 몸의 변화」 4칸은 `challenges`와 `checkins`·`weight_log`에서 계산되므로 v3.0 원칙 #5대로 저장하지 않습니다.
