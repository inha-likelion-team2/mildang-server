package app.mildang;

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
 * CLAUDE.md §2 «절대 불변 조건 — 테스트로 고정».
 * 다른 테스트가 각자의 플로우를 검증한다면, 이 파일은 규칙 자체를 잠근다.
 * 여기가 깨지면 기능이 아니라 제품의 약속이 깨진 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvariantsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String challengeId;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String auth() {
        return "Bearer " + token;
    }

    /** balance = total − spent − prepaid 가 지금 이 순간 성립하는지 (§0.10) */
    private void assertIdentity(String moment) throws Exception {
        MvcResult result = mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        JsonNode b = json(result).get("budget");
        int total = b.get("total").asInt();
        int spent = b.get("spent").asInt();
        int prepaid = b.get("prepaid").asInt();
        int balance = b.get("balance").asInt();
        org.assertj.core.api.Assertions.assertThat(balance)
                .as("항등식 위반 (%s): %d − %d − %d ≠ %d", moment, total, spent, prepaid, balance)
                .isEqualTo(total - spent - prepaid);
    }

    private String analyze(String query, String kind) throws Exception {
        MvcResult result = mvc.perform(post("/analyses/text")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"" + query + "\",\"context\":{\"challengeId\":\""
                                + challengeId + "\",\"kind\":\"" + kind + "\"}}"))
                .andExpect(status().isOk()).andReturn();
        return json(result).get("id").asText();
    }

    private String item(String query, String kind, String weekday) throws Exception {
        String body = weekday == null
                ? "{\"kind\":\"" + kind + "\",\"analysisId\":\"" + analyze(query, kind) + "\"}"
                : "{\"kind\":\"" + kind + "\",\"weekday\":\"" + weekday + "\",\"analysisId\":\""
                        + analyze(query, kind) + "\"}";
        MvcResult result = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json(result).get("id").asText();
    }

    @Test
    @Order(1)
    @DisplayName("준비 — W1 확정")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"invariants-user","deviceId":"d-inv"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        challengeId = json(created).get("id").asText();

        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":225}
                                """))
                .andExpect(status().isOk());
        assertIdentity("예산 확정 직후");
    }

    @Test
    @Order(2)
    @DisplayName("★불변 1 — balance = total − spent − prepaid 가 모든 전이 후에 성립한다")
    void balanceIdentityHoldsThroughout() throws Exception {
        String meal = item("라면", "MEAL", null);
        assertIdentity("항목 생성 후");

        mvc.perform(post("/items/" + meal + "/record").header("Authorization", auth()))
                .andExpect(status().isOk());
        assertIdentity("기록 후");

        mvc.perform(post("/items/" + meal + "/record").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyProcessed").value(true));
        assertIdentity("기록 재요청(멱등) 후");

        String promise = item("치킨", "PROMISE", "FRI");
        mvc.perform(post("/items/" + promise + "/prepay").header("Authorization", auth()))
                .andExpect(status().isOk());
        assertIdentity("선차감 후");

        // PREPAID → RECORDED 는 prepaid→spent «이동» — 잔액이 변하면 안 된다
        MvcResult before = mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        int balanceBefore = json(before).get("budget").get("balance").asInt();

        mvc.perform(post("/items/" + promise + "/record").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyProcessed").value(true))
                .andExpect(jsonPath("$.budget.balance").value(balanceBefore))
                .andExpect(jsonPath("$.budget.prepaid").value(0));
        assertIdentity("PREPAID→RECORDED 이동 후");

        mvc.perform(get("/items?kind=MEAL&status=CANCELED").header("Authorization", auth()))
                .andExpect(status().isOk());
        assertIdentity("조회 후");
    }

    @Test
    @Order(3)
    @DisplayName("★불변 7 — 흥정은 10턴까지, 11번째는 409 (429 아님)")
    void turnCapIsNineOnePlusConflict() throws Exception {
        String meal = item("떡볶이", "MEAL", null);
        MvcResult opened = mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + meal + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.maxTurns").value(10))
                .andReturn();
        String haggleId = json(opened).get("id").asText();

        for (int turn = 1; turn <= 10; turn++) {
            mvc.perform(post("/haggles/" + haggleId + "/messages")
                            .header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"text\":\"반만 먹을게\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.turn").value(turn))
                    .andExpect(jsonPath("$.turnsLeft").value(10 - turn));
        }

        mvc.perform(post("/haggles/" + haggleId + "/messages")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"한 번만 더\"}"))
                .andExpect(status().isConflict()) // ★ 429가 아니라 409 — 비즈니스 규칙이지 레이트 리밋이 아니다
                .andExpect(jsonPath("$.error.code").value("HAGGLE_TURN_EXCEEDED"));

        assertIdentity("10턴 소진 후");
    }

    @Test
    @Order(4)
    @DisplayName("★불변 4 — EXPIRED 항목으로는 흥정을 열 수 없다 (409 ITEM_EXPIRED)")
    void expiredItemCannotOpenHaggle() throws Exception {
        // 흥정만 하고 기록하지 않은 항목을 만든 뒤 하루를 넘겨 배치를 돌리면 EXPIRED가 된다
        String meal = item("김밥", "MEAL", null);
        MvcResult opened = mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + meal + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated()).andReturn();
        String haggleId = json(opened).get("id").asText();
        mvc.perform(post("/haggles/" + haggleId + "/messages")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"반만 먹을게\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/haggles/" + haggleId + "/close").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("HAGGLED"));

        mvc.perform(post("/demo/advance-day")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":1}"))
                .andExpect(status().isOk());
        mvc.perform(post("/demo/run-batch")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobs\":[\"ITEM_EXPIRY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + meal + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ITEM_EXPIRED"));

        // 만료돼도 «드셨어요» 소급 기록은 되어야 하고, 그 뒤에도 항등식은 성립한다
        mvc.perform(post("/items/" + meal + "/record").header("Authorization", auth()))
                .andExpect(status().isOk());
        assertIdentity("EXPIRED 소급 기록 후");
    }

    @Test
    @Order(5)
    @DisplayName("★불변 5 — 초과(음수 잔액)는 어떤 전이도 막지 않는다")
    void overspendNeverBlocks() throws Exception {
        // 잔액이 음수가 될 때까지 기록 (중복 병합 창을 피하려고 서로 다른 메뉴를 쓴다)
        for (String menu : new String[] {"치킨", "라면", "떡볶이", "칼국수", "빵"}) {
            String id = item(menu, "MEAL", null);
            mvc.perform(post("/items/" + id + "/record").header("Authorization", auth()))
                    .andExpect(status().isOk());
        }
        MvcResult current = mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        int balance = json(current).get("budget").get("balance").asInt();
        org.assertj.core.api.Assertions.assertThat(balance)
                .as("이 테스트는 잔액이 음수인 상태를 전제로 한다").isNegative();
        assertIdentity("초과 상태");

        // 음수인데도 기록·흥정이 모두 가능해야 한다
        String more = item("김밥", "MEAL", null);
        mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + more + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated())
                // 프레임이 «덜 깊어질까»로 바뀔 뿐, 거절하지 않는다
                .andExpect(jsonPath("$.frame").value("REDUCE_OVERFLOW"));
        mvc.perform(post("/items/" + more + "/record").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overflow").exists());
        assertIdentity("초과 상태에서 추가 기록 후");
    }
}
