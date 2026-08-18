package app.mildang.haggle;

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
 * 요금제별 밀당 대화 횟수 (결제 화면 141:288 「AI 밀당 대화 40회」).
 * 팀 결정 2026-08-18 — <b>대화 1번 = 1회</b>, 1주 20 · 2주 40 · 4주 무제한, 다 쓰면 막고 안내.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HaggleQuotaTest {

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

    /** 항목 하나 만들고 그 항목으로 대화를 연다 — 열린 세션 id를 돌려준다 */
    private String openHaggle(String presetId) throws Exception {
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"" + presetId + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String itemId = json(item).get("id").asText();

        MvcResult session = mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + itemId + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated()).andReturn();
        // 확정해 두지 않으면 같은 프리셋을 다시 담을 때 3초 중복 병합에 걸려 같은 항목이 된다
        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", auth()))
                .andExpect(status().isOk());
        return json(session).get("id").asText();
    }

    @Test
    @Order(1)
    @DisplayName("준비 — W1(20회) 확정")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"quota-user","deviceId":"d-q"}
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
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"budget":225}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @Order(2)
    @DisplayName("★ 대시보드가 남은 횟수를 알려준다 — 벽에 부딪히기 전에 보여주려고")
    void dashboardShowsRemaining() throws Exception {
        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.haggleQuota.limit").value(20))
                .andExpect(jsonPath("$.haggleQuota.used").value(0))
                .andExpect(jsonPath("$.haggleQuota.remaining").value(20))
                .andExpect(jsonPath("$.haggleQuota.unlimited").value(false));
    }

    @Test
    @Order(3)
    @DisplayName("★ 대화를 열면 한 번씩 줄어든다")
    void openingCounts() throws Exception {
        openHaggle("pst_ramen");
        openHaggle("pst_bread");

        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.haggleQuota.used").value(2))
                .andExpect(jsonPath("$.haggleQuota.remaining").value(18));
    }

    @Test
    @Order(4)
    @DisplayName("★ 같은 항목을 다시 여는 재흥정은 새로 세지 않는다 — 마음 바꿀 때마다 깎이면 안 된다")
    void reopeningDoesNotCount() throws Exception {
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"pst_tteok\"}"))
                .andExpect(status().isCreated()).andReturn();
        String itemId = json(item).get("id").asText();

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/haggles")
                            .header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"itemId\":\"" + itemId + "\",\"entryPoint\":\"FREE\"}"))
                    .andExpect(status().isCreated());
        }

        // 세 번 열었지만 같은 항목이라 1회만 쓴다 (앞 테스트의 2 + 1)
        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.haggleQuota.used").value(3));

        // 다음 테스트가 같은 프리셋을 새 항목으로 담을 수 있게 확정해 둔다 (3초 중복 병합 회피)
        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", auth()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    @DisplayName("★★ 다 쓰면 409로 막고, 기록은 그대로 된다고 알려준다")
    void blocksWhenExhausted() throws Exception {
        // 남은 17번을 모두 쓴다
        String[] presets = {"pst_ramen", "pst_bread", "pst_tteok", "pst_chicken"};
        for (int i = 0; i < 17; i++) {
            openHaggle(presets[i % presets.length]);
        }

        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.haggleQuota.remaining").value(0));

        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"pst_ramen\"}"))
                .andExpect(status().isCreated()).andReturn();
        String itemId = json(item).get("id").asText();

        mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + itemId + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HAGGLE_QUOTA_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("기록과 선차감은 그대로")));

        // 막힌 건 대화뿐 — 기록은 계속 된다
        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", auth()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(6)
    @DisplayName("★ 4주는 무제한 — limit·remaining이 null이다")
    void w4IsUnlimited() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"quota-w4","deviceId":"d-q4"}
                                """))
                .andExpect(status().isOk()).andReturn();
        String w4Token = json(login).get("accessToken").asText();

        // 4주는 유료라 결제가 먼저다 (demo는 MOCK 통과)
        MvcResult pay = mvc.perform(post("/payments/checkout")
                        .header("Authorization", "Bearer " + w4Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W4\",\"provider\":\"MOCK\"}"))
                .andExpect(status().isCreated()).andReturn();
        String paymentId = json(pay).get("id").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + w4Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W4\",\"paymentId\":\"" + paymentId + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/challenges/" + json(created).get("id").asText() + "/budget")
                        .header("Authorization", "Bearer " + w4Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"budget":225}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + w4Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.haggleQuota.unlimited").value(true))
                .andExpect(jsonPath("$.haggleQuota.limit").doesNotExist())
                .andExpect(jsonPath("$.haggleQuota.remaining").doesNotExist());
    }
}
