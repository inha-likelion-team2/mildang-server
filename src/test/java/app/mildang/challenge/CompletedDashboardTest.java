package app.mildang.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
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
 * 마지막 날을 넘겼을 때 대시보드가 «끝났다»를 알려주는지 (FE 제보 2026-08-19).
 *
 * <p>이전에는 신규 유저와 방금 완주한 사람이 <b>똑같은 404</b>를 받아서, 프론트가 둘을 구분하지
 * 못하고 완주자도 온보딩으로 되돌려보냈다. 완주 후엔 리포트를 부를 challengeId조차 알 수 없었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompletedDashboardTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private String token() throws Exception {
        String body = "{\"provider\":\"KAKAO\",\"idToken\":\"done-%s\",\"deviceId\":\"t\"}"
                .formatted(UUID.randomUUID());
        String res = mvc.perform(post("/auth/social").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res).get("accessToken").asString();
    }

    private JsonNode current(String token) throws Exception {
        String res = mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res);
    }

    /**
     * 이 클래스가 만든 데이터를 지운다.
     *
     * <p>{@code PREPAID_CONVERT} 배치는 «전 사용자» 대상이다(실제 05:00 배치가 그러니까).
     * 시드가 만드는 선차감 항목을 남겨두면 DemoFlowTest의 converted 개수에 섞여 들어간다.
     */
    private void reset(String token) throws Exception {
        mvc.perform(post("/demo/reset").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private void advance(String token, int days) throws Exception {
        mvc.perform(post("/demo/advance-day").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":" + days + "}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("6일차·7일차는 ACTIVE로 남고, 마지막 날을 넘기면 COMPLETED로 온다")
    void lastDayThenCompleted() throws Exception {
        String t = token();
        mvc.perform(post("/demo/seed").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"DAY4_ACTIVE\"}"))
                .andExpect(status().isOk());

        advance(t, 2);
        JsonNode day6 = current(t);
        assertThat(day6.get("challenge").get("dayIndex").asInt()).isEqualTo(6);
        assertThat(day6.get("challenge").get("status").asString()).isEqualTo("ACTIVE");

        advance(t, 1);
        JsonNode day7 = current(t);
        assertThat(day7.get("challenge").get("dayIndex").asInt()).isEqualTo(7);
        assertThat(day7.get("challenge").get("status").asString())
                .as("마지막 날은 아직 진행 중이다 — 여기서 끝내면 하루를 잃는다")
                .isEqualTo("ACTIVE");

        advance(t, 1);
        JsonNode after = current(t);
        assertThat(after.get("challenge").get("status").asString())
                .as("마지막 날을 넘기면 404가 아니라 COMPLETED로 와야 프론트가 리포트로 보낼 수 있다")
                .isEqualTo("COMPLETED");
        assertThat(after.get("challenge").get("id").asString())
                .as("리포트를 부르려면 id가 필요하다")
                .startsWith("chl_");
        reset(t);
    }

    @Test
    @DisplayName("COMPLETED로 받은 id로 리포트를 바로 부를 수 있다")
    void reportReachableFromCurrent() throws Exception {
        String t = token();
        mvc.perform(post("/demo/seed").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"DAY4_ACTIVE\"}"))
                .andExpect(status().isOk());
        advance(t, 4);

        JsonNode after = current(t);
        String id = after.get("challenge").get("id").asString();
        assertThat(after.get("challenge").get("status").asString()).isEqualTo("COMPLETED");

        mvc.perform(get("/challenges/" + id + "/report").header("Authorization", "Bearer " + t))
                .andExpect(status().isOk());
        reset(t);
    }

    @Test
    @DisplayName("advance-day가 완주를 그 자리에서 알려주고, 완주 후에도 계속 넘길 수 있다")
    void advanceDayReportsCompletionAndKeepsWorking() throws Exception {
        String t = token();
        mvc.perform(post("/demo/seed").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"DAY4_ACTIVE\"}"))
                .andExpect(status().isOk());

        // 4일차 → 7일차(마지막 날)까지는 계속 ACTIVE
        for (int expected = 5; expected <= 7; expected++) {
            JsonNode adv = advanceAndRead(t);
            assertThat(adv.get("dayIndex").asInt()).isEqualTo(expected);
            assertThat(adv.get("status").asString()).isEqualTo("ACTIVE");
        }

        // 마지막 날을 넘기는 순간 «그 응답에서» 완주가 드러나야 한다.
        // 예전엔 여기서 ACTIVE가 돌아와 «안 넘어갔다»로 보였다.
        JsonNode done = advanceAndRead(t);
        assertThat(done.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(done.get("completed").asBoolean()).isTrue();
        assertThat(done.get("challengeId").asString()).startsWith("chl_");

        // 완주 후에도 404가 아니어야 한다 — 시연 중에 버튼이 죽으면 안 된다
        JsonNode more = advanceAndRead(t);
        assertThat(more.get("status").asString()).isEqualTo("COMPLETED");

        reset(t);
    }

    private JsonNode advanceAndRead(String token) throws Exception {
        String res = mvc.perform(post("/demo/advance-day").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(res);
    }

    @Test
    @DisplayName("챌린지를 한 번도 안 만든 사람은 그대로 404 — 온보딩으로 가야 한다")
    void brandNewUserStill404() throws Exception {
        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("끝난 챌린지에는 기록할 수 없다 — 쓰기 경로는 여전히 404")
    void writesStillRejectedAfterCompletion() throws Exception {
        String t = token();
        mvc.perform(post("/demo/seed").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"scenario\":\"DAY4_ACTIVE\"}"))
                .andExpect(status().isOk());
        advance(t, 4);

        assertThat(current(t).get("challenge").get("status").asString()).isEqualTo("COMPLETED");

        mvc.perform(post("/items").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"pst_ramen\"}"))
                .andExpect(status().isNotFound());
        reset(t);
    }
}
