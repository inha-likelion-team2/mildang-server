package app.mildang.analysis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * "잘 모르겠어요" 후보 칩이 막다른 길이 아닌지 — 배포 전 감사 C3.
 * 후보를 눌렀는데 또 422가 뜨면 사용자가 항목을 만들 방법이 없다(무한 루프).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CandidateResolvableTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("★ 모르는 메뉴 → 후보 3개 → 각 후보가 전부 해결된다 (루프 없음)")
    void everyCandidateResolves() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"candidate-user","deviceId":"d-cd"}
                                """))
                .andExpect(status().isOk()).andReturn();
        String token = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        String challengeId = json(created).get("id").asText();
        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":225}
                                """))
                .andExpect(status().isOk());

        MvcResult failed = mvc.perform(post("/analyses/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"피자\",\"context\":{\"challengeId\":\""
                                + challengeId + "\",\"kind\":\"MEAL\"}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("ANALYSIS_FAILED"))
                .andExpect(jsonPath("$.error.detail.candidates.length()").value(3))
                .andReturn();

        JsonNode candidates = json(failed).get("error").get("detail").get("candidates");
        for (JsonNode candidate : candidates) {
            String name = candidate.get("name").asText();
            int points = candidate.get("points").asInt();
            // 후보를 그대로 재분석하면 200이어야 하고, 칩에 보인 가격과 같아야 한다
            mvc.perform(post("/analyses/text")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"" + name + "\",\"context\":{\"challengeId\":\""
                                    + challengeId + "\",\"kind\":\"MEAL\"}}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.resolved").value(true))
                    .andExpect(jsonPath("$.menu.name").value(name))
                    .andExpect(jsonPath("$.menu.points").value(points));
        }
    }
}
