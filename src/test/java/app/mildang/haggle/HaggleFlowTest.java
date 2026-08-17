package app.mildang.haggle;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

/** 핵심 루프 E2E — 입력→분석→흥정→합의 반영(잔액 불변)→기록 (명세 §12.1, Fake AI 기준) */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HaggleFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String challengeId;
    String itemId;
    String haggleId;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @Order(1)
    @DisplayName("준비 — 로그인·챌린지·라면 항목")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"haggle-e2e-user","deviceId":"d-h1"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        challengeId = json(created).get("id").asText();

        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":225}
                                """))
                .andExpect(status().isOk());

        MvcResult analysis = mvc.perform(post("/analyses/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"라면","context":{"challengeId":"%s","kind":"MEAL"}}
                                """.formatted(challengeId)))
                .andExpect(status().isOk()).andReturn();

        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\""
                                + json(analysis).get("id").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        itemId = json(item).get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("세션 시작 — turn 0·frame SAVE·칩 4개·agreed null (§7.1)")
    void open() throws Exception {
        MvcResult result = mvc.perform(post("/haggles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + itemId + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxTurns").value(10))
                .andExpect(jsonPath("$.turn").value(0))
                .andExpect(jsonPath("$.frame").value("SAVE"))
                .andExpect(jsonPath("$.balance").value(225))
                .andExpect(jsonPath("$.target.name").value("라면"))
                .andExpect(jsonPath("$.agreed").isEmpty())
                .andExpect(jsonPath("$.chips.length()").value(4))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        haggleId = json(result).get("id").asText();
    }

    @Test
    @Order(3)
    @DisplayName("한 턴 — '반만 먹을게' → 40 제안·시뮬레이션·closeButtonLabel (§7.2)")
    void message() throws Exception {
        mvc.perform(post("/haggles/" + haggleId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"반만 먹을게\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turn").value(1))
                .andExpect(jsonPath("$.turnsLeft").value(9))
                .andExpect(jsonPath("$.reply.hasProposal").value(true))
                .andExpect(jsonPath("$.proposal.points").value(40))
                .andExpect(jsonPath("$.proposal.lever").value("AMOUNT"))
                .andExpect(jsonPath("$.agreed.points").value(40))
                .andExpect(jsonPath("$.simulation.adjusted.balanceAfter").value(185))
                .andExpect(jsonPath("$.simulation.original.balanceAfter").value(145))
                .andExpect(jsonPath("$.closeButtonLabel").value("대화 종료 · 40으로 반영"));
    }

    @Test
    @Order(4)
    @DisplayName("종료 — 합의값만 반영, 잔액은 절대 불변 (§7.3 불변 조건)")
    void close() throws Exception {
        mvc.perform(post("/haggles/" + haggleId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.haggle.status").value("CLOSED"))
                .andExpect(jsonPath("$.item.status").value("HAGGLED"))
                .andExpect(jsonPath("$.item.adjusted.points").value(40))
                .andExpect(jsonPath("$.item.effective.points").value(40));

        // ★ 잔액 불변 확인 — 차감은 record에서만
        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.balance").value(225))
                .andExpect(jsonPath("$.budget.spent").value(0));

        // 종료된 세션에 메시지 → 409
        mvc.perform(post("/haggles/" + haggleId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"더 깎아줘\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HAGGLE_SESSION_CLOSED"));
    }

    @Test
    @Order(5)
    @DisplayName("기록 — 합의값 40으로 차감 (225→185), 오프닝에 지난 합의 언급(재흥정)")
    void recordAndReopen() throws Exception {
        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.balance").value(185));

        // 같은 메뉴 재분석·재흥정 — lastAgreement가 오프닝에 언급 (부록 A #3)
        MvcResult analysis = mvc.perform(post("/analyses/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"라면","context":{"challengeId":"%s","kind":"MEAL"}}
                                """.formatted(challengeId)))
                .andExpect(status().isOk()).andReturn();
        MvcResult item2 = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\""
                                + json(analysis).get("id").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        MvcResult opened = mvc.perform(post("/haggles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + json(item2).get("id").asText()
                                + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated()).andReturn();
        String opening = json(opened).get("opening").asText();
        org.assertj.core.api.Assertions.assertThat(opening).contains("40");

        // ✕ 변경 없이 나가기 — 항목 원래값 유지
        mvc.perform(delete("/haggles/" + json(opened).get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(6)
    @DisplayName("제안 하한 — '1포인트로 해줘'로 라면 80을 1까지 끌어내릴 수 없다")
    void proposalFloor() throws Exception {
        MvcResult analysis = mvc.perform(post("/analyses/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"query":"라면","context":{"challengeId":"%s","kind":"MEAL"}}
                                """.formatted(challengeId)))
                .andExpect(status().isOk()).andReturn();
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\""
                                + json(analysis).get("id").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        MvcResult opened = mvc.perform(post("/haggles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + json(item).get("id").asText()
                                + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated()).andReturn();

        // 하한(라면 80 → round(80/3) = 27) 미만 요구는 게이트가 잡아 폴백으로 교정된다
        MvcResult turn = mvc.perform(post("/haggles/" + json(opened).get("id").asText() + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"1포인트로 해줘\"}"))
                .andExpect(status().isOk()).andReturn();

        int proposed = json(turn).get("proposal").get("points").asInt();
        org.assertj.core.api.Assertions.assertThat(proposed).isGreaterThanOrEqualTo(27);

        // 합의값도 하한 아래로 내려가지 않는다
        org.assertj.core.api.Assertions.assertThat(json(turn).get("agreed").get("points").asInt())
                .isGreaterThanOrEqualTo(27);
    }
}
