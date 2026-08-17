package app.mildang.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * 화면 «온보딩_03» — 설문으로 추천값을 받고 슬라이더로 조정해 확정한다.
 * 확정 피그마 기준: "1주 예산을 설정해볼까요? / 400밀 / 가볍게 ↔ 넉넉하게 /
 * 나중에도 언제든지 조정할 수 있어요".
 * 화면 픽셀이 아니라 «이 화면이 필요로 하는 데이터를 API가 주는가»를 고정한다 —
 * 디자인이 더 바뀌어도 이 테스트는 살아남아야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BudgetSliderTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String challengeId;
    int min;
    int max;
    int step;
    int recommended;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String auth() {
        return "Bearer " + token;
    }

    @Test
    @Order(1)
    @DisplayName("준비 — 로그인 + W1 생성")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"slider-user","deviceId":"d-sl"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        challengeId = json(created).get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("★ 설문 → 슬라이더 범위를 준다 (min·max·step·recommended)")
    void estimateReturnsSlider() throws Exception {
        MvcResult result = mvc.perform(post("/challenges/" + challengeId + "/budget/estimate")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slider.min").exists())
                .andExpect(jsonPath("$.slider.max").exists())
                .andExpect(jsonPath("$.slider.step").value(5))
                .andExpect(jsonPath("$.slider.recommended").exists())
                .andReturn();

        JsonNode s = json(result).get("slider");
        min = s.get("min").asInt();
        max = s.get("max").asInt();
        step = s.get("step").asInt();
        recommended = s.get("recommended").asInt();

        org.assertj.core.api.Assertions.assertThat(min).isLessThan(recommended);
        org.assertj.core.api.Assertions.assertThat(recommended).isLessThan(max);
        // 추천값이 트랙 끝에 붙어 있으면 슬라이더로서 쓸모가 없다
        double pos = (recommended - min) / (double) (max - min);
        org.assertj.core.api.Assertions.assertThat(pos)
                .as("추천값이 트랙의 %.0f%% 지점 — 가운데 근처여야 좌우로 조정할 여지가 있다", pos * 100)
                .isBetween(0.2, 0.8);
    }

    @Test
    @Order(3)
    @DisplayName("★ 제안값이 아닌 임의값도 확정된다 — 슬라이더가 만들 수 있는 값이면")
    void confirmsArbitrarySliderValue() throws Exception {
        int picked = recommended + step * 3; // 사용자가 «넉넉하게» 쪽으로 민 값
        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"survey\":{\"noodle\":\"2-3\",\"bread\":\"0-1\",\"snack\":\"4+\"},"
                                + "\"budget\":" + picked + "}")) // optionKey 없이
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.budget").value(picked))   // W1이라 총액 = 주간값
                .andExpect(jsonPath("$.balance").value(picked));
    }

    @Test
    @Order(4)
    @DisplayName("범위 밖·단위 어긋난 값은 거절하고, 무엇이 잘못됐는지 알려준다")
    void rejectsOutOfRange() throws Exception {
        for (int bad : new int[] {min - step, max + step, recommended + 1}) {
            mvc.perform(patch("/challenges/" + challengeId + "/budget")
                            .header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"budget\":" + bad + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.error.field").value("budget"))
                    .andExpect(jsonPath("$.error.message")
                            .value(org.hamcrest.Matchers.containsString(String.valueOf(min))));
        }
    }

    @Test
    @Order(5)
    @DisplayName("★ «나중에도 언제든지 조정할 수 있어요» — 확정 후에도 예산을 바꿀 수 있다")
    void adjustsAfterConfirm() throws Exception {
        // 먼저 좀 써둔다 — 조정이 지출을 건드리면 안 된다
        MvcResult analysis = mvc.perform(post("/analyses/text")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"라면\",\"context\":{\"challengeId\":\""
                                + challengeId + "\",\"kind\":\"MEAL\"}}"))
                .andExpect(status().isOk()).andReturn();
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\""
                                + json(analysis).get("id").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/items/" + json(item).get("id").asText() + "/record")
                        .header("Authorization", auth()))
                .andExpect(status().isOk());

        int adjusted = min + step; // «가볍게» 쪽으로 크게 내린다
        mvc.perform(patch("/challenges/" + challengeId + "/budget")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":" + adjusted + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget").value(adjusted));

        // 항등식이 유지되고, 이미 쓴 80은 그대로 남아 있어야 한다
        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.total").value(adjusted))
                .andExpect(jsonPath("$.budget.spent").value(80))
                .andExpect(jsonPath("$.budget.balance").value(adjusted - 80));
    }

    @Test
    @Order(6)
    @DisplayName("예산을 지출보다 낮춰도 막지 않는다 — 초과는 항상 허용 (불변 조건 5)")
    void allowsAdjustBelowSpent() throws Exception {
        mvc.perform(patch("/challenges/" + challengeId + "/budget")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"budget\":" + min + "}"))
                .andExpect(status().isOk());

        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.balance").value(min - 80))
                .andExpect(jsonPath("$.budget.gaugePercent").value(0)); // 음수는 0으로 클램프
    }
}
