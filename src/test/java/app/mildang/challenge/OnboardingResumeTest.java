package app.mildang.challenge;

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
 * 온보딩 중 이탈해도 계정이 잠기지 않는지 — 배포 전 감사 C2.
 * 예산 확정 전에 나가면 current는 404인데 재생성은 409여서 빠져나갈 API가 없었다.
 * 화면 2에 뒤로가기가 없어 브라우저 뒤로가기가 유일한 탈출구인데 그게 곧 함정이었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OnboardingResumeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String firstChallengeId;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String auth() {
        return "Bearer " + token;
    }

    private MvcResult createChallenge(String period) throws Exception {
        return mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"" + period + "\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
    }

    @Test
    @Order(1)
    @DisplayName("준비 — 로그인 후 예산 확정 없이 챌린지만 생성 (이탈 상황)")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"resume-user","deviceId":"d-rs"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();

        firstChallengeId = json(createChallenge("W1")).get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("★ 같은 기간으로 다시 시작 — 409가 아니라 기존 챌린지를 이어서 준다")
    void resumesSamePeriod() throws Exception {
        mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstChallengeId))
                .andExpect(jsonPath("$.status").value("ONBOARDING"))
                .andExpect(jsonPath("$.period").value("W1"));
    }

    @Test
    @Order(3)
    @DisplayName("무료 기간을 바꿔서 다시 시작 — 결제가 없으므로 기간을 갈아끼운다")
    void switchesPeriodWhenUnpaid() throws Exception {
        // W1(무료) → W2는 결제가 필요하므로 결제 없이 오면 402여야 한다
        mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W2\",\"paymentId\":null}"))
                .andExpect(status().isPaymentRequired());

        // 결제를 마치고 오면 같은 온보딩 챌린지가 W2로 바뀐다 (새 챌린지가 생기지 않는다)
        MvcResult pay = mvc.perform(post("/payments/checkout")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W2\",\"provider\":\"MOCK\",\"receipt\":\"r-resume\"}"))
                .andExpect(status().isCreated()).andReturn();

        mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W2\",\"paymentId\":\""
                                + json(pay).get("id").asText() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstChallengeId))
                .andExpect(jsonPath("$.period").value("W2"))
                .andExpect(jsonPath("$.totalDays").value(14));
    }

    @Test
    @Order(4)
    @DisplayName("예산까지 확정하면 그때부터는 409 — 진행 중 챌린지는 보호된다")
    void activeChallengeStillBlocks() throws Exception {
        mvc.perform(post("/challenges/" + firstChallengeId + "/budget")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":225}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHALLENGE_IN_PROGRESS"));
    }

    @Test
    @Order(5)
    @DisplayName("기간이 끝난 ACTIVE는 새 챌린지 생성 시 자동 정리된다 — 갇히지 않는다")
    void completesStaleActive() throws Exception {
        // 기간을 넘겨 ACTIVE인 채로 endsAt만 지난 상태를 만든다 (앞 단계에서 W2=14일로 바뀌었다)
        mvc.perform(post("/demo/advance-day")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":15}"))
                .andExpect(status().isOk());

        // 이 시점에 current는 404지만, 새 챌린지는 만들어져야 한다 (예전엔 409로 막혔다)
        mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ONBOARDING"));

        // 끝난 챌린지는 COMPLETED로 확정돼 리포트를 볼 수 있다
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/challenges/" + firstChallengeId + "/report")
                        .header("Authorization", auth()))
                .andExpect(status().isOk());
    }
}
