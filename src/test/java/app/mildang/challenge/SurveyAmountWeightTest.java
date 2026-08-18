package app.mildang.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 설문의 amount·weightKg가 실제로 예산에 반영되는지 (FE 요청 2026-08-19).
 *
 * <p>이름이 어긋나면 값이 조용히 버려진다 — 400도 안 나고 결과만 같아지므로, «다른 값을 보내면
 * 다른 예산이 나온다»를 고정해두지 않으면 회귀를 못 잡는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SurveyAmountWeightTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private String token() throws Exception {
        String body = """
                {"provider":"KAKAO","idToken":"survey-%s","deviceId":"t"}
                """.formatted(java.util.UUID.randomUUID());
        String res = mvc.perform(post("/auth/social").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asString();
    }

    private String challenge(String token) throws Exception {
        String res = mvc.perform(post("/challenges").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"period\":\"W1\"}"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("id").asString();
    }

    private JsonNode estimate(String token, String cid, String surveyJson) throws Exception {
        String res = mvc.perform(post("/challenges/" + cid + "/budget/estimate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"survey\":" + surveyJson + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res);
    }

    private static final String FREQ = "\"noodle\":\"2-3\",\"bread\":\"0-1\",\"snack\":\"4+\"";

    @Test
    @DisplayName("amount가 예산을 움직인다 — SMALL < NORMAL < LARGE")
    void amountChangesEstimate() throws Exception {
        String t = token();
        int small = estimate(t, challenge(t), "{" + FREQ + ",\"amount\":\"SMALL\"}")
                .get("estimatedWeekly").asInt();
        String t2 = token();
        int normal = estimate(t2, challenge(t2), "{" + FREQ + ",\"amount\":\"NORMAL\"}")
                .get("estimatedWeekly").asInt();
        String t3 = token();
        int large = estimate(t3, challenge(t3), "{" + FREQ + ",\"amount\":\"LARGE\"}")
                .get("estimatedWeekly").asInt();

        assertThat(small).isLessThan(normal);
        assertThat(normal).isLessThan(large);
    }

    @Test
    @DisplayName("예전 이름 portion도 계속 받는다 — amount와 같은 결과")
    void portionStillAccepted() throws Exception {
        String t = token();
        int viaAmount = estimate(t, challenge(t), "{" + FREQ + ",\"amount\":\"LARGE\"}")
                .get("estimatedWeekly").asInt();
        String t2 = token();
        int viaPortion = estimate(t2, challenge(t2), "{" + FREQ + ",\"portion\":\"LARGE\"}")
                .get("estimatedWeekly").asInt();

        assertThat(viaPortion).isEqualTo(viaAmount);
    }

    @Test
    @DisplayName("weightKg가 예산을 움직인다 — 50kg < 65.5kg < 90kg")
    void weightChangesEstimate() throws Exception {
        String t = token();
        int light = estimate(t, challenge(t), "{" + FREQ + ",\"amount\":\"NORMAL\",\"weightKg\":50.0}")
                .get("estimatedWeekly").asInt();
        String t2 = token();
        int mid = estimate(t2, challenge(t2), "{" + FREQ + ",\"amount\":\"NORMAL\",\"weightKg\":65.5}")
                .get("estimatedWeekly").asInt();
        String t3 = token();
        int heavy = estimate(t3, challenge(t3), "{" + FREQ + ",\"amount\":\"NORMAL\",\"weightKg\":90.0}")
                .get("estimatedWeekly").asInt();

        assertThat(light).isLessThan(mid);
        assertThat(mid).isLessThan(heavy);
    }

    @Test
    @DisplayName("체중을 안 보내면 곱수 1.0 — 기존 결과 그대로")
    void weightIsOptional() throws Exception {
        assertThat(BudgetPolicy.weightMultiplier(null)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("체중 보정은 ±15%에서 멈춘다 — 설문 자체를 뒤집지 않는다")
    void weightMultiplierIsClamped() {
        assertThat(BudgetPolicy.weightMultiplier(new BigDecimal("30"))).isEqualTo(0.85);
        assertThat(BudgetPolicy.weightMultiplier(new BigDecimal("200"))).isEqualTo(1.15);
        assertThat(BudgetPolicy.weightMultiplier(new BigDecimal("60"))).isEqualTo(1.0);
    }

    @Test
    @DisplayName("budget이 survey.weightKg를 시작 체중으로 저장한다")
    void confirmStoresWeightFromSurvey() throws Exception {
        String t = token();
        String cid = challenge(t);
        JsonNode est = estimate(t, cid, "{" + FREQ + ",\"amount\":\"NORMAL\",\"weightKg\":65.5}");
        int budget = est.get("recommended").asInt();

        mvc.perform(post("/challenges/" + cid + "/budget")
                        .header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"survey\":{" + FREQ + ",\"amount\":\"NORMAL\",\"weightKg\":65.5},"
                                + "\"optionKey\":\"AS_IS\",\"budget\":" + budget + "}"))
                .andExpect(status().isOk());

        String cur = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/challenges/current").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode weights = om.readTree(cur).get("weights");
        assertThat(weights).isNotEmpty();
        assertThat(weights.get(0).get("weightKg").asDouble()).isEqualTo(65.5);
    }

    @Test
    @DisplayName("최상위 weightKg도 계속 받는다 — 예전 자리 호환")
    void confirmAcceptsTopLevelWeight() throws Exception {
        String t = token();
        String cid = challenge(t);
        int budget = estimate(t, cid, "{" + FREQ + ",\"amount\":\"NORMAL\"}").get("recommended").asInt();

        mvc.perform(post("/challenges/" + cid + "/budget")
                        .header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"survey\":{" + FREQ + ",\"amount\":\"NORMAL\"},"
                                + "\"optionKey\":\"AS_IS\",\"budget\":" + budget + ",\"weightKg\":70.0}"))
                .andExpect(status().isOk());

        String cur = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/challenges/current").header("Authorization", "Bearer " + t))
                .andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(cur).get("weights").get(0).get("weightKg").asDouble()).isEqualTo(70.0);
    }

    @Test
    @DisplayName("situation은 예산을 바꾸지 않는다 — 팀 결정(2026-08-17) 고정")
    void situationDoesNotChangeBudget() throws Exception {
        String t = token();
        int meal = estimate(t, challenge(t),
                "{" + FREQ + ",\"amount\":\"NORMAL\",\"situation\":\"MEAL\"}").get("estimatedWeekly").asInt();
        String t2 = token();
        int late = estimate(t2, challenge(t2),
                "{" + FREQ + ",\"amount\":\"NORMAL\",\"situation\":\"LATE_NIGHT\"}").get("estimatedWeekly").asInt();

        assertThat(late).isEqualTo(meal);
    }
}
