package app.mildang.analysis;

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

/** 3c 텍스트 분석 연동 E2E — Fake AI 게이트웨이(local 기본 fake=true) 기준. 명세 §5.1·§12.1 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AnalysisFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String challengeId;
    String analysisId;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @Order(1)
    @DisplayName("준비 — 로그인 + W1 챌린지 시작")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"analysis-e2e-user","deviceId":"d-a1"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        token = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated())
                .andReturn();
        challengeId = json(created).get("id").asText();

        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":225}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("라면 분석 — unit·근거·신뢰도 채워진 anl_* 생성 (§5.1)")
    void analyzeRamen() throws Exception {
        MvcResult result = mvc.perform(post("/analyses/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"라면","context":{"challengeId":"%s","kind":"MEAL"}}
                                """.formatted(challengeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.menu.name").value("라면"))
                .andExpect(jsonPath("$.menu.unit").value("1봉지"))
                .andExpect(jsonPath("$.menu.points").value(80))
                .andExpect(jsonPath("$.menu.confidence").value("CERTAIN"))
                .andExpect(jsonPath("$.candidates").doesNotExist())
                .andReturn();
        analysisId = json(result).get("id").asText();
    }

    @Test
    @Order(3)
    @DisplayName("식별 실패 — 422 + 후보 정확히 3개 (§5.1)")
    void analyzeUnknown() throws Exception {
        mvc.perform(post("/analyses/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"그거","context":{"challengeId":"%s","kind":"MEAL"}}
                                """.formatted(challengeId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("ANALYSIS_FAILED"))
                .andExpect(jsonPath("$.error.detail.candidates.length()").value(3))
                .andExpect(jsonPath("$.error.detail.candidates[0].name").value("칼국수"));
    }

    @Test
    @Order(4)
    @DisplayName("분석 결과로 항목 생성 → 기록 — §12.1 시퀀스 완주 (잔액 85→5)")
    void createItemFromAnalysis() throws Exception {
        MvcResult created = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\"" + analysisId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source.type").value("TEXT"))
                .andExpect(jsonPath("$.source.refId").value(analysisId))
                .andExpect(jsonPath("$.original.name").value("라면"))
                .andExpect(jsonPath("$.original.points").value(80))
                .andReturn();
        String itemId = json(created).get("id").asText();

        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.balance").value(145));
    }

    @Test
    @Order(5)
    @DisplayName("최근 칩 — resolved 이름 최신순 최대 3개 (§5.2)")
    void recentChips() throws Exception {
        mvc.perform(post("/analyses/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"떡볶이","context":{"challengeId":"%s","kind":"MEAL"}}
                                """.formatted(challengeId)))
                .andExpect(status().isOk());

        mvc.perform(get("/analyses/recent").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recent[0].name").value("떡볶이"))
                .andExpect(jsonPath("$.recent[1].name").value("라면"));
    }

    @Test
    @Order(6)
    @DisplayName("없는 analysisId로 항목 생성 → 404 (§6.3)")
    void missingAnalysis() throws Exception {
        mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\"anl_missing\"}"))
                .andExpect(status().isNotFound());
    }
}
