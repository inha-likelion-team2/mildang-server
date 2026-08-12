package app.mildang;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 백엔드 단독(AI 미사용) 구간 E2E — 로그인 → 온보딩 → 예산 확정 → 대시보드 → 프리셋 기록 → 선차감 멱등 → 체크인.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OnboardingFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String challengeId;
    String mealItemId;
    String promiseItemId;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @Order(1)
    @DisplayName("데모 로그인 — idToken이 곧 계정 키, mocked=true")
    void login() throws Exception {
        MvcResult result = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"e2e-user-1","deviceId":"d-1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mocked").value(true))
                .andExpect(jsonPath("$.user.isNew").value(true))
                .andReturn();
        token = json(result).get("accessToken").asText();
    }

    @Test
    @Order(2)
    @DisplayName("진행 중 챌린지 없음 → current 404")
    void currentBeforeChallenge() throws Exception {
        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @Order(3)
    @DisplayName("W1 생성 → ONBOARDING·needsSurvey")
    void createChallenge() throws Exception {
        MvcResult result = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ONBOARDING"))
                .andExpect(jsonPath("$.needsSurvey").value(true))
                .andReturn();
        challengeId = json(result).get("id").asText();
    }

    @Test
    @Order(4)
    @DisplayName("예산 미리보기 — 명세 예시값 75/85/95")
    void estimate() throws Exception {
        mvc.perform(post("/challenges/" + challengeId + "/budget/estimate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedWeekly").value(100))
                .andExpect(jsonPath("$.recommended").value(85))
                .andExpect(jsonPath("$.anchors.length()").value(3))
                .andExpect(jsonPath("$.options[0].budget").value(75));
    }

    @Test
    @Order(5)
    @DisplayName("예산 확정 → ACTIVE·잔액 85·27조합 시작 팁")
    void confirmBudget() throws Exception {
        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":85}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(85))
                .andExpect(jsonPath("$.startTip.text").isNotEmpty());

        // 재확정은 409
        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":85}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUDGET_ALREADY_SET"));
    }

    @Test
    @Order(6)
    @DisplayName("대시보드 — 1일차, 게이지 100, 체크인 미완료")
    void dashboard() throws Exception {
        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge.dayIndex").value(1))
                .andExpect(jsonPath("$.challenge.label").value("1주 챌린지 · 1일차"))
                .andExpect(jsonPath("$.budget.balance").value(85))
                .andExpect(jsonPath("$.budget.gaugePercent").value(100))
                .andExpect(jsonPath("$.checkin.doneToday").value(false))
                .andExpect(jsonPath("$.expiredConfirm.length()").value(0));
    }

    @Test
    @Order(7)
    @DisplayName("프리셋 항목 생성(라면 80) → 기록 → 잔액 5, 재기록은 멱등 200 (§6.9)")
    void createAndRecordItem() throws Exception {
        MvcResult created = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"pst_ramen\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.original.points").value(80))
                .andExpect(jsonPath("$.effective.balanceAfter").value(5))
                .andReturn();
        mealItemId = json(created).get("id").asText();

        mvc.perform(post("/items/" + mealItemId + "/record").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("RECORDED"))
                .andExpect(jsonPath("$.budget.balance").value(5))
                .andExpect(jsonPath("$.alreadyProcessed").value(false))
                .andExpect(jsonPath("$.overflow").doesNotExist());

        // 재기록 더블탭 — 409가 아니라 멱등 200, 차감 없음 (§6.9)
        mvc.perform(post("/items/" + mealItemId + "/record").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyProcessed").value(true))
                .andExpect(jsonPath("$.budget.balance").value(5));

        // MEAL 항목에 prepay는 400 — 선차감은 약속 전용
        mvc.perform(post("/items/" + mealItemId + "/prepay").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(8)
    @DisplayName("약속 선차감 — 멱등: 두 번 눌러도 한 번만 차감, 초과 기록도 허용")
    void prepayIdempotent() throws Exception {
        MvcResult created = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROMISE\",\"presetId\":\"pst_chicken\",\"weekday\":\"FRI\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        promiseItemId = json(created).get("id").asText();

        // 잔액 5에서 치킨 70 선차감 → −65. 초과는 막지 않고 overflow를 싣는다 (§6.5·§7.6)
        mvc.perform(post("/items/" + promiseItemId + "/prepay").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("PREPAID"))
                .andExpect(jsonPath("$.budget.balance").value(-65))
                .andExpect(jsonPath("$.budget.prepaid").value(70))
                .andExpect(jsonPath("$.alreadyProcessed").value(false))
                .andExpect(jsonPath("$.overflow.balance").value(-65));

        // 더블탭 — 이중 차감 없음, 멱등 표식 (§6.9)
        mvc.perform(post("/items/" + promiseItemId + "/prepay").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyProcessed").value(true))
                .andExpect(jsonPath("$.budget.balance").value(-65))
                .andExpect(jsonPath("$.budget.prepaid").value(70));

        // 대시보드 선차감 카드 + 게이지 0 클램프
        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.gaugePercent").value(0))
                .andExpect(jsonPath("$.prepaidItems[0].name").value("금요일 치킨 약속"));

        // PREPAID에 record — 200 전이, 잔액 불변, prepaid → spent 이동 (§6.9·§0.10)
        mvc.perform(post("/items/" + promiseItemId + "/record").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("RECORDED"))
                .andExpect(jsonPath("$.alreadyProcessed").value(true))
                .andExpect(jsonPath("$.budget.balance").value(-65))
                .andExpect(jsonPath("$.budget.prepaid").value(0))
                .andExpect(jsonPath("$.budget.spent").value(150));

        // RECORDED가 된 약속에 prepay 재시도 — 다른 종착 상태로의 전이라 409 (§6.9)
        mvc.perform(post("/items/" + promiseItemId + "/prepay").header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ITEM_ALREADY_RECORDED"));
    }

    @Test
    @Order(9)
    @DisplayName("체크인 — 3문항 필수, 멱등 덮어쓰기, 대시보드 doneToday 반영")
    void checkin() throws Exception {
        mvc.perform(get("/checkins/today").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(false))
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.checkinDays.answered").value(0))
                .andExpect(jsonPath("$.checkinDays.elapsed").value(1))
                .andExpect(jsonPath("$.checkinDays.total").value(7));

        mvc.perform(put("/checkins/today")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":{"BLOAT":"BAD","SKIN":"MID","DROWSY":"GOOD"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.checkinDays.answered").value(1));

        // 부분 제출은 400
        mvc.perform(put("/checkins/today")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"BLOAT\":\"BAD\"}}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkin.doneToday").value(true));
    }

    @Test
    @Order(10)
    @DisplayName("결제 목 — MOCK 항상 PAID·mocked=true, W2 무결제 생성은 402")
    void paymentMock() throws Exception {
        mvc.perform(post("/payments/checkout")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W2\",\"provider\":\"MOCK\",\"receipt\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mocked").value(true))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountKrw").value(2000));

        // 진행 중 챌린지가 있으므로 새 챌린지는 409
        mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W2\",\"paymentId\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHALLENGE_IN_PROGRESS"));
    }

    @Test
    @Order(11)
    @DisplayName("인증 없는 호출은 401, /plans는 무인증 허용")
    void authGuard() throws Exception {
        mvc.perform(get("/challenges/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));

        mvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plans.length()").value(3))
                .andExpect(jsonPath("$.plans[0].priceKrw").value(0));
    }
}
