package app.mildang.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 심사용 데모 시나리오 E2E — seed → 대시보드 → 선차감 → advance-day → run-batch → 소급 기록 (명세 §14.5·§14.6) */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @Order(1)
    @DisplayName("시드 계정 로그인 — demo-judge-02 → 심사위원2")
    void loginJudge() throws Exception {
        MvcResult result = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"demo-judge-02","deviceId":"judge-device-2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname").value("심사위원2"))
                .andReturn();
        token = json(result).get("accessToken").asText();
    }

    @Test
    @Order(2)
    @DisplayName("DAY4_ACTIVE 시드 — §14.5 검산 그대로: spent 13 · prepaid 20 · balance 52 · gauge 61")
    void seedDay4() throws Exception {
        mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"DAY4_ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mocked").value(true))
                .andExpect(jsonPath("$.balance").value(52));

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge.dayIndex").value(4))
                .andExpect(jsonPath("$.challenge.label").value("1주 챌린지 · 4일차"))
                .andExpect(jsonPath("$.budget.balance").value(52))
                .andExpect(jsonPath("$.budget.spent").value(13))
                .andExpect(jsonPath("$.budget.prepaid").value(20))
                .andExpect(jsonPath("$.budget.gaugePercent").value(61))
                .andExpect(jsonPath("$.prepaidItems[0].name").value("수요일 점심 약속"))
                .andExpect(jsonPath("$.checkin.doneToday").value(false));

        // 미기록 항목 없음 — summary는 필터와 무관한 고정값 (§6.2)
        mvc.perform(get("/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.summary.totalPoints").value(0));
    }

    @Test
    @Order(3)
    @DisplayName("advance-day 7 → run-batch: 수요일 지난 선차감이 RECORDED로 이동 (잔액 불변)")
    void batchDemo() throws Exception {
        mvc.perform(post("/demo/advance-day")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mocked").value(true));

        mvc.perform(post("/demo/run-batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobs\":[\"PREPAID_CONVERT\",\"ITEM_EXPIRY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.converted").value(1))
                .andExpect(jsonPath("$.expired").value(0));
    }

    @Test
    @Order(4)
    @DisplayName("EXPIRED_CONFIRM 시드 → 확인 시트 → '드셨어요' 소급 기록")
    void expiredConfirmFlow() throws Exception {
        mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"EXPIRED_CONFIRM\"}"))
                .andExpect(status().isOk());

        MvcResult current = mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredConfirm.length()").value(1))
                .andExpect(jsonPath("$.expiredConfirm[0].points").value(40))
                .andReturn();
        String itemId = json(current).get("expiredConfirm").get(0).get("id").asText();

        // "드셨어요" → EXPIRED에서 record (소급, §6.9)
        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("RECORDED"))
                .andExpect(jsonPath("$.alreadyProcessed").value(false))
                .andExpect(jsonPath("$.budget.balance").value(45));
    }

    @Test
    @Order(5)
    @DisplayName("LOW_BALANCE·W2_DAY8·W4_DAY12 시드 (§14.5 수치) + reset")
    void otherScenarios() throws Exception {
        mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"LOW_BALANCE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(5));

        mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"W2_DAY8\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(90));

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge.dayIndex").value(8))
                .andExpect(jsonPath("$.budget.total").value(170))
                .andExpect(jsonPath("$.pace.diff").value(17))
                .andExpect(jsonPath("$.pace.state").value("AHEAD"));

        mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"W4_DAY12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(280));

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge.dayIndex").value(12))
                .andExpect(jsonPath("$.challenge.period").value("W4"))
                .andExpect(jsonPath("$.budget.total").value(340))
                .andExpect(jsonPath("$.budget.gaugePercent").value(82));

        mvc.perform(post("/demo/reset").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(6)
    @DisplayName("시나리오를 빠뜨리면 500이 아니라 안내되는 400")
    void missingScenarioIsGuided() throws Exception {
        // @Valid가 빠져 있어 @NotNull이 무시됐고, switch(null)이 NPE로 터져 500이 나갔다
        mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/demo/run-batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        // 목록에 없는 이름은 그대로 400
        mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"NOPE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("scenario"));
    }
}
