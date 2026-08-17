package app.mildang.weight;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/**
 * 체중 기록 — 화면 3 대시보드의 «1일차 58kg · 2일차 55kg».
 * 값만 남기고 예산·잔액·리포트에는 관여하지 않는다 (팀 결정 2026-08-17).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WeightLogTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String auth() {
        return "Bearer " + token;
    }

    private void putWeight(String kg) throws Exception {
        mvc.perform(put("/weights/today")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\":" + kg + "}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1)
    @DisplayName("준비 — W1 확정")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"weight-user","deviceId":"d-wg"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/challenges/" + json(created).get("id").asText() + "/budget")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"budget":225}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("★ 기록 전에는 오늘 값이 비어 있고, 넣으면 오늘 값 + 시리즈가 온다")
    void recordsToday() throws Exception {
        mvc.perform(get("/weights").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").isEmpty())
                .andExpect(jsonPath("$.series.length()").value(0));

        putWeight("58.0");

        mvc.perform(get("/weights").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today.weightKg").value(58.0))
                .andExpect(jsonPath("$.today.dayIndex").value(1))
                .andExpect(jsonPath("$.series.length()").value(1));
    }

    @Test
    @Order(3)
    @DisplayName("하루 한 건 — 다시 보내면 덮어쓴다 (체크인과 같은 규칙)")
    void idempotentPerDay() throws Exception {
        putWeight("57.4");
        mvc.perform(get("/weights").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today.weightKg").value(57.4))
                .andExpect(jsonPath("$.series.length()").value(1)); // 늘지 않는다
    }

    @Test
    @Order(4)
    @DisplayName("★ 대시보드가 그래프 재료를 함께 준다 — 별도 호출 없이")
    void dashboardCarriesTheSeries() throws Exception {
        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weights.length()").value(1))
                .andExpect(jsonPath("$.weights[0].weightKg").value(57.4))
                .andExpect(jsonPath("$.weights[0].dayIndex").value(1));
    }

    @Test
    @Order(5)
    @DisplayName("★ 체중은 예산·잔액에 관여하지 않는다")
    void doesNotTouchBudget() throws Exception {
        MvcResult before = mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        int balance = json(before).get("budget").get("balance").asInt();

        putWeight("60.0");

        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.balance").value(balance))
                .andExpect(jsonPath("$.budget.spent").value(0));
    }

    @Test
    @Order(6)
    @DisplayName("★ 컨디션 체크인에서 체중을 함께 보낼 수 있다 — 같은 날 기록에 합쳐진다")
    void checkinCarriesWeight() throws Exception {
        mvc.perform(put("/checkins/today")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":{"BLOAT":"BAD","SKIN":"MID","DROWSY":"GOOD"},"weightKg":56.2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true))
                .andExpect(jsonPath("$.weightKg").value(56.2));

        // 체크인 화면을 다시 열면 넣었던 값이 그대로 보인다
        mvc.perform(get("/checkins/today").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weightKg").value(56.2));

        // 별도 엔드포인트로 넣은 것과 같은 자리에 쌓인다 — 하루 한 건이라 개수가 늘지 않는다
        mvc.perform(get("/weights").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today.weightKg").value(56.2))
                .andExpect(jsonPath("$.series.length()").value(1));
    }

    @Test
    @Order(7)
    @DisplayName("체중 없이 컨디션만 보내도 된다 — 「건너뛰어도 괜찮아요」")
    void checkinWithoutWeight() throws Exception {
        mvc.perform(put("/checkins/today")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"answers":{"BLOAT":"GOOD","SKIN":"GOOD","DROWSY":"GOOD"}}
                                """))
                .andExpect(status().isOk())
                // 앞서 넣어둔 체중은 지워지지 않는다
                .andExpect(jsonPath("$.weightKg").value(56.2));
    }

    @Test
    @Order(8)
    @DisplayName("사람 몸무게가 아닌 값은 오타로 보고 거절한다")
    void rejectsAbsurdValues() throws Exception {
        for (String bad : new String[] {"5.0", "500.0", "-3"}) {
            mvc.perform(put("/weights/today")
                            .header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"weightKg\":" + bad + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }
}
