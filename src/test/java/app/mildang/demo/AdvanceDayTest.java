package app.mildang.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 「하루 넘기기」 회귀 — FE 제보 2026-08-19·2026-08-20 */
@SpringBootTest
@AutoConfigureMockMvc
class AdvanceDayTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String login(String idToken) throws Exception {
        MvcResult result = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"KAKAO\",\"idToken\":\"" + idToken + "\",\"deviceId\":\"d\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    /** W1 하나를 ACTIVE까지 만들고 id를 돌려준다 */
    private String startChallenge(String token) throws Exception {
        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        String id = json(created).get("id").asText();
        mvc.perform(post("/challenges/" + id + "/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":225}
                                """))
                .andExpect(status().isOk());
        return id;
    }

    private record Row(LocalDate date) {
    }

    @Test
    @DisplayName("날짜 이동은 «옮기는 방향» 순서로 — 뒤로는 이른 날짜부터, 앞으로는 늦은 날짜부터")
    void shiftOrderKeepsUniqueDatesFree() {
        // DB가 돌려주는 물리적 순서는 날짜순이 아니다 — 배포본(Postgres)에서 실제로 나온 순서
        List<Row> scrambled = List.of(new Row(LocalDate.of(2026, 8, 17)),
                new Row(LocalDate.of(2026, 8, 12)),
                new Row(LocalDate.of(2026, 8, 16)),
                new Row(LocalDate.of(2026, 8, 18)));

        // 하루 뒤로 — 이른 날짜부터 옮겨야 목적지가 먼저 빈다
        assertThat(DemoController.inShiftOrder(scrambled, Row::date, 1))
                .extracting(Row::date)
                .containsExactly(LocalDate.of(2026, 8, 12), LocalDate.of(2026, 8, 16),
                        LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 18));

        // 앞으로 옮기면 반대 — 늦은 날짜부터
        assertThat(DemoController.inShiftOrder(scrambled, Row::date, -1))
                .extracting(Row::date)
                .containsExactly(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 17),
                        LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 12));
    }

    @Test
    @DisplayName("체중·체크인이 며칠치 쌓여도 완주까지 계속 넘어간다 (FE 제보 2026-08-20)")
    void advancesWithWeightsAndCheckins() throws Exception {
        String token = login("advance-day-weights");
        startChallenge(token);

        // 하루치 «체크인 + 체중»을 남기고 넘기기를 7번 — 체중 행이 하루씩 쌓인다
        for (int day = 1; day <= 7; day++) {
            mvc.perform(put("/checkins/today")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"answers\":{\"BLOAT\":\"GOOD\",\"SKIN\":\"GOOD\",\"DROWSY\":\"MID\"},"
                                    + "\"weightKg\":" + (58.0 - day * 0.2) + "}"))
                    .andExpect(status().isOk());

            mvc.perform(post("/demo/advance-day")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"days\":1}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.dayIndex").value(Math.min(7, day + 1)));
        }

        mvc.perform(post("/demo/advance-day")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    @DisplayName("완주 뒤 예산 확정 전 새 챌린지가 있어도 500이 아니라 «시작한 챌린지»를 넘긴다")
    void skipsNotYetStartedChallenge() throws Exception {
        String token = login("advance-day-onboarding");
        String finished = startChallenge(token);

        for (int i = 0; i < 7; i++) {
            mvc.perform(post("/demo/advance-day")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"days\":1}"))
                    .andExpect(status().isOk());
        }

        // 새 판을 만들어 두고 예산은 아직 안 정한 상태 — startedAt이 null인 껍데기가 «가장 최근»이 된다
        mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startedAt").doesNotExist());

        mvc.perform(post("/demo/advance-day")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"days\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(finished))
                .andExpect(jsonPath("$.completed").value(true));
    }
}
