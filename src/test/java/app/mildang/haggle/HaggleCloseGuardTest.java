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

/**
 * 흥정 종료가 이미 확정된 항목을 되살리지 않는지 — 배포 전 감사 C1.
 * 세션은 항목의 종착 전이보다 오래 산다(대화창을 열어둔 채 목록에서 기록 가능).
 * 가드가 없으면 RECORDED 항목이 HAGGLED로 되돌아가 목록에 재등장하고 두 번 차감된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HaggleCloseGuardTest {

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

    /** 라면 80 항목 하나 만들고 흥정으로 40까지 합의 (close는 하지 않음) */
    private String[] itemWithAgreement() throws Exception {
        MvcResult analysis = mvc.perform(post("/analyses/text")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"라면\",\"context\":{\"challengeId\":\""
                                + challengeId + "\",\"kind\":\"MEAL\"}}"))
                .andExpect(status().isOk()).andReturn();
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"analysisId\":\"" + json(analysis).get("id").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String itemId = json(item).get("id").asText();

        MvcResult opened = mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + itemId + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated()).andReturn();
        String haggleId = json(opened).get("id").asText();

        mvc.perform(post("/haggles/" + haggleId + "/messages")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"반만 먹을게\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreed.points").value(40));
        return new String[] {itemId, haggleId};
    }

    @Test
    @Order(1)
    @DisplayName("준비 — 로그인·W1 확정")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"close-guard-user","deviceId":"d-cg"}
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
    }

    @Test
    @Order(2)
    @DisplayName("★ 기록된 항목에 close — 되살아나지 않고 두 번 차감되지 않는다")
    void doesNotResurrectRecordedItem() throws Exception {
        String[] ids = itemWithAgreement();
        String itemId = ids[0];
        String haggleId = ids[1];

        // 대화창을 열어둔 채 목록에서 원래값 그대로 기록 (80 차감)
        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("RECORDED"))
                .andExpect(jsonPath("$.budget.balance").value(145))
                .andExpect(jsonPath("$.budget.spent").value(80));

        // 열려 있던 대화창에서 «대화 종료» — 세션은 닫히되 항목은 그대로여야 한다
        mvc.perform(post("/haggles/" + haggleId + "/close").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.haggle.status").value("CLOSED"))
                .andExpect(jsonPath("$.item.status").value("RECORDED"))
                .andExpect(jsonPath("$.item.effective.points").value(80))
                .andExpect(jsonPath("$.item.adjusted").isEmpty());

        // 미기록 목록에 되살아나지 않는다
        mvc.perform(get("/items?kind=MEAL&status=PENDING,HAGGLED").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        // 다시 기록해도 추가 차감 없음 (멱등)
        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyProcessed").value(true))
                .andExpect(jsonPath("$.budget.spent").value(80));
    }

    @Test
    @Order(3)
    @DisplayName("삭제된 항목에 close — 되살아나지 않는다")
    void doesNotResurrectCanceledItem() throws Exception {
        String[] ids = itemWithAgreement();

        mvc.perform(delete("/items/" + ids[0]).header("Authorization", auth()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/haggles/" + ids[1] + "/close").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("CANCELED"))
                .andExpect(jsonPath("$.item.adjusted").isEmpty());
        mvc.perform(get("/items?kind=MEAL&status=PENDING,HAGGLED").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    @Order(4)
    @DisplayName("선차감된 약속에 close — prepaid가 갇히지 않는다")
    void doesNotStrandPrepaid() throws Exception {
        MvcResult analysis = mvc.perform(post("/analyses/text")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"치킨\",\"context\":{\"challengeId\":\""
                                + challengeId + "\",\"kind\":\"PROMISE\"}}"))
                .andExpect(status().isOk()).andReturn();
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROMISE\",\"weekday\":\"FRI\",\"analysisId\":\""
                                + json(analysis).get("id").asText() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String itemId = json(item).get("id").asText();

        MvcResult opened = mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + itemId + "\",\"entryPoint\":\"PROMISE\"}"))
                .andExpect(status().isCreated()).andReturn();
        String haggleId = json(opened).get("id").asText();
        mvc.perform(post("/haggles/" + haggleId + "/messages")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"반만 먹을게\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/items/" + itemId + "/prepay").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("PREPAID"));

        // close해도 PREPAID가 유지돼야 배치가 나중에 회수할 수 있다
        mvc.perform(post("/haggles/" + haggleId + "/close").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.status").value("PREPAID"));
    }

    @Test
    @Order(5)
    @DisplayName("재흥정을 합의 없이 닫으면 — 지난 합의값이 남고, 인사말이 그 사실과 맞는다")
    void farewellMatchesItemState() throws Exception {
        String[] ids = itemWithAgreement();
        mvc.perform(post("/haggles/" + ids[1] + "/close").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.effective.points").value(40));

        // 다시 열고 제안 없이 바로 닫기
        MvcResult reopened = mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + ids[0] + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/haggles/" + json(reopened).get("id").asText() + "/close")
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.effective.points").value(40))
                // "원래값 그대로예요"라고 하면 값(40)과 모순된다
                .andExpect(jsonPath("$.farewell").value(org.hamcrest.Matchers.containsString("지난 합의")));
    }
}
