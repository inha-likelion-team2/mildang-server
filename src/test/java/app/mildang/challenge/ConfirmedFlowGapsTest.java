package app.mildang.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.mildang.common.time.LogicalDate;
import java.time.Instant;
import java.time.LocalDate;
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
 * 확정 와이어프레임 갱신분(2026-08-17 저녁) — 렌더로 대조하며 찾은 세 가지.
 *
 * <p>① 온보딩 설문의 「체중은 어떻게 되나요?」 ② 약속 화면의 「언제 예요? · 날짜 입력」(요일이 아니라 날짜)
 * ③ 「최근 내역」 칩이 값까지 보여준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConfirmedFlowGapsTest {

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

    @Test
    @Order(1)
    @DisplayName("★ 온보딩 설문에서 시작 체중을 함께 받는다 — 1일차 기록으로 남는다")
    void onboardingTakesStartingWeight() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"gaps-user","deviceId":"d-gap"}
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
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},
                                 "budget":225, "weightKg":61.5}
                                """))
                .andExpect(status().isOk());

        // 온보딩 중에는 체중을 남길 자리가 없어 확정 직후에 1일차로 들어간다
        mvc.perform(get("/weights").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today.weightKg").value(61.5))
                .andExpect(jsonPath("$.today.dayIndex").value(1));

        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.days[0].weighed").value(true));
    }

    @Test
    @Order(2)
    @DisplayName("체중 없이 확정해도 된다 — 설문에서 건너뛸 수 있다")
    void weightIsOptionalAtOnboarding() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"gaps-noweight","deviceId":"d-gap2"}
                                """))
                .andExpect(status().isOk()).andReturn();
        String other = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/challenges/" + json(created).get("id").asText() + "/budget")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"budget":225}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/weights").header("Authorization", "Bearer " + other))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").isEmpty());
    }

    @Test
    @Order(3)
    @DisplayName("★ 약속을 날짜로 잡는다 — 화면이 「언제 예요? · 날짜 입력」이라서")
    void promiseTakesADate() throws Exception {
        LocalDate today = LogicalDate.of(Instant.now());
        LocalDate theDayAfter = today.plusDays(2);

        mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROMISE\",\"presetId\":\"pst_chicken\",\"promiseDate\":\""
                                + theDayAfter + "\"}"))
                .andExpect(status().isCreated())
                // 날짜에서 요일을 서버가 뽑는다 — 클라이언트가 둘 다 보낼 필요가 없다
                .andExpect(jsonPath("$.weekday").value(
                        app.mildang.common.model.Weekday.of(theDayAfter.getDayOfWeek()).name()));
    }

    @Test
    @Order(4)
    @DisplayName("요일로 보내던 기존 방식도 그대로 받는다")
    void weekdayStillWorks() throws Exception {
        mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROMISE\",\"presetId\":\"pst_ramen\",\"weekday\":\"FRI\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weekday").value("FRI"));
    }

    @Test
    @Order(5)
    @DisplayName("★ 챌린지 기간 밖 날짜는 막는다 — 어디에도 귀속되지 않는 약속이 생긴다")
    void promiseOutsideChallengeIsRejected() throws Exception {
        LocalDate wayLater = LogicalDate.of(Instant.now()).plusDays(30);

        mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROMISE\",\"presetId\":\"pst_bread\",\"promiseDate\":\""
                                + wayLater + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("promiseDate"));
    }

    @Test
    @Order(6)
    @DisplayName("날짜도 요일도 없으면 안내되는 400")
    void promiseNeedsWhen() throws Exception {
        mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROMISE\",\"presetId\":\"pst_bread\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("promiseDate"));
    }

    @Test
    @Order(7)
    @DisplayName("★ 「최근 내역」 칩이 값까지 준다 — 화면이 「라면 80」으로 보여준다")
    void recentChipsCarryPoints() throws Exception {
        MvcResult current = mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        String challengeId = json(current).get("challenge").get("id").asText();

        mvc.perform(post("/analyses/text")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"라면\",\"context\":{\"challengeId\":\""
                                + challengeId + "\",\"kind\":\"MEAL\"}}"))
                .andExpect(status().isOk());

        mvc.perform(get("/analyses/recent").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent[0].name").value("라면"))
                .andExpect(jsonPath("$.recent[0].points").isNumber())
                .andExpect(jsonPath("$.recent[0].unit").isNotEmpty());
    }
}
