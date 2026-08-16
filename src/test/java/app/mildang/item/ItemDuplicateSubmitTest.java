package app.mildang.item;

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

/**
 * 중복 제출 방어 — 팀 이슈 #1 추가 피드백.
 * 증상: "칼국수" 입력 후 Enter를 누르면 같은 항목이 2개 생긴다.
 * 원인: 한글 IME는 조합 중 Enter에서 keydown이 두 번 뜬다(조합 확정 + Enter).
 * 클라 가드가 1차 방어지만, 가드가 없는 클라이언트도 보호하려고 서버가 짧은 창 안의
 * 같은 항목 재생성을 병합한다. 사람이 의도적으로 두 번 담기엔 3초는 너무 짧다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ItemDuplicateSubmitTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String challengeId;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    /** 3c 입력 한 번 = 분석 1건. Enter가 두 번 먹으면 분석도 두 번 생긴다 */
    private String analyze(String query) throws Exception {
        MvcResult result = mvc.perform(post("/analyses/text")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + query + "\",\"context\":{\"challengeId\":\""
                                + challengeId + "\",\"kind\":\"MEAL\"}}"))
                .andExpect(status().isOk()).andReturn();
        return json(result).get("id").asText();
    }

    private MvcResult createItem(String analysisId) throws Exception {
        return mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\"" + analysisId + "\"}"))
                .andExpect(status().isCreated()).andReturn();
    }

    @Test
    @Order(1)
    @DisplayName("준비 — 로그인·W1 확정")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"dup-submit-user","deviceId":"d-dup"}
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
    }

    @Test
    @Order(2)
    @DisplayName("Enter 이중 발생 재현 — 분석 2건이어도 항목은 1개만 남는다")
    void mergesDoubleSubmit() throws Exception {
        // IME Enter 두 번 = 분석 두 번 = analysisId 두 개 (여기가 핵심 — 같은 id 재사용이 아니다)
        String first = analyze("라면");
        String second = analyze("라면");
        org.assertj.core.api.Assertions.assertThat(first).isNotEqualTo(second);

        String itemId = json(createItem(first)).get("id").asText();
        // 두 번째 요청은 새 항목을 만들지 않고 방금 만든 항목을 돌려준다
        mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\"" + second + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(itemId));

        mvc.perform(get("/items?kind=MEAL&status=PENDING,HAGGLED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.summary.count").value(1))
                .andExpect(jsonPath("$.summary.totalPoints").value(80));
    }

    @Test
    @Order(3)
    @DisplayName("다른 메뉴는 병합하지 않는다")
    void keepsDifferentMenus() throws Exception {
        createItem(analyze("치킨"));

        mvc.perform(get("/items?kind=MEAL&status=PENDING,HAGGLED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @Order(4)
    @DisplayName("창이 지나면 같은 메뉴도 정상적으로 하나 더 담긴다 — 두 번 먹는 건 막지 않는다")
    void allowsSameMenuAfterWindow() throws Exception {
        // 3초 창을 넘긴 상황을 만든다 (하루 두 번 라면은 정상 사용)
        Thread.sleep(3200);
        createItem(analyze("라면"));

        mvc.perform(get("/items?kind=MEAL&status=PENDING,HAGGLED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.summary.totalPoints").value(230)); // 라면 80 × 2 + 치킨 70
    }
}
