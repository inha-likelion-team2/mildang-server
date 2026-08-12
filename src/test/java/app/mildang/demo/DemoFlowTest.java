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
    @DisplayName("DAY4_ACTIVE 시드 → 4일차 대시보드 + 3b 흥정 항목 + 3a 약속")
    void seedDay4() throws Exception {
        mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"DAY4_ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mocked").value(true))
                .andExpect(jsonPath("$.balance").value(50));

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge.dayIndex").value(4))
                .andExpect(jsonPath("$.challenge.label").value("1주 챌린지 · 4일차"))
                .andExpect(jsonPath("$.budget.balance").value(50))
                .andExpect(jsonPath("$.budget.spent").value(35))
                .andExpect(jsonPath("$.checkin.doneToday").value(false));

        // 3b: 흥정 완료 라면(80→40) + 3a: 약속 치킨 대기
        mvc.perform(get("/items").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.summary.totalPoints").value(110));
    }

    @Test
    @Order(3)
    @DisplayName("치킨 선차감 → advance-day 7 → run-batch: PREPAID→RECORDED 전환 + 라면 EXPIRED")
    void batchDemo() throws Exception {
        MvcResult items = mvc.perform(get("/items?kind=PROMISE").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String promiseId = json(items).get("items").get(0).get("id").asText();

        mvc.perform(post("/items/" + promiseId + "/prepay").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.balance").value(-20))
                .andExpect(jsonPath("$.overflow.balance").value(-20));

        mvc.perform(post("/demo/advance-day")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mocked").value(true));

        // 요일 지난 선차감 전환 1건 + 흥정 항목 만료 1건
        mvc.perform(post("/demo/run-batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobs\":[\"PREPAID_CONVERT\",\"ITEM_EXPIRY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.converted").value(1))
                .andExpect(jsonPath("$.expired").value(1));
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
    @DisplayName("LOW_BALANCE·W4_DAY12·FRESH 시드 + reset")
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
                        .content("{\"scenario\":\"W4_DAY12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(240));

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge.dayIndex").value(12))
                .andExpect(jsonPath("$.challenge.period").value("W4"));

        mvc.perform(post("/demo/reset").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
