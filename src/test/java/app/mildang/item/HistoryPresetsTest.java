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
 * 「자주 먹는 것」 (§6.7) — 최근 4주에 실제로 기록한 것을 빈도순으로.
 *
 * <p>핵심은 <b>표시 가격이 항상 original</b>이라는 것이다. 과거 합의값을 칩에 쓰면 다음에 담을 때
 * 그 값이 새 기준선이 되고, 또 흥정하면 더 내려가 기준선이 판마다 무너진다 (부록 A #3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HistoryPresetsTest {

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

    /** 항목 생성 → 기록. 생성된 항목 id를 돌려준다 */
    private String eat(String presetId) throws Exception {
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"" + presetId + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = json(item).get("id").asText();
        mvc.perform(post("/items/" + id + "/record").header("Authorization", auth()))
                .andExpect(status().isOk());
        return id;
    }

    @Test
    @Order(1)
    @DisplayName("준비 — W1 확정")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"presets-user","deviceId":"d-pre"}
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
    @DisplayName("★ 이력이 없으면 기본 4종 — 처음 쓰는 사람도 칩이 비지 않는다")
    void fallsBackToDefaults() throws Exception {
        mvc.perform(get("/presets").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("DEFAULT"))
                .andExpect(jsonPath("$.presets.length()").value(4));
    }

    @Test
    @Order(3)
    @DisplayName("★ 먹은 것이 칩에 올라오고 source가 HISTORY로 바뀐다")
    void historyLeadsTheChips() throws Exception {
        eat("pst_tteok");

        mvc.perform(get("/presets").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HISTORY"))
                .andExpect(jsonPath("$.presets.length()").value(4))
                .andExpect(jsonPath("$.presets[0].name").value("떡볶이"))
                // 모자란 자리는 기본 4종으로 채우되 같은 메뉴가 두 번 오지 않는다
                .andExpect(jsonPath("$.presets[1].name").value("라면"))
                .andExpect(jsonPath("$.presets[2].name").value("빵"))
                .andExpect(jsonPath("$.presets[3].name").value("치킨"));
    }

    @Test
    @Order(4)
    @DisplayName("★ 자주 먹은 것이 앞으로 온다")
    void mostFrequentFirst() throws Exception {
        eat("pst_chicken");
        eat("pst_chicken");

        mvc.perform(get("/presets").header("Authorization", auth()))
                .andExpect(status().isOk())
                // 치킨 2회 > 떡볶이 1회
                .andExpect(jsonPath("$.presets[0].name").value("치킨"))
                .andExpect(jsonPath("$.presets[1].name").value("떡볶이"));
    }

    @Test
    @Order(5)
    @DisplayName("★★ 흥정으로 깎은 값은 칩에 쓰지 않는다 — 기준선이 판마다 내려가면 안 된다")
    void chipsAlwaysShowOriginal() throws Exception {
        // 라면 80을 흥정해서 낮춰 기록한다
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"pst_ramen\"}"))
                .andExpect(status().isCreated()).andReturn();
        String itemId = json(item).get("id").asText();

        MvcResult session = mvc.perform(post("/haggles")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + itemId + "\",\"entryPoint\":\"FREE\"}"))
                .andExpect(status().isCreated()).andReturn();
        String haggleId = json(session).get("id").asText();

        mvc.perform(post("/haggles/" + haggleId + "/messages")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"절반으로 줄일게요\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/haggles/" + haggleId + "/close").header("Authorization", auth()))
                .andExpect(status().isOk());
        mvc.perform(post("/items/" + itemId + "/record").header("Authorization", auth()))
                .andExpect(status().isOk());

        // 라면을 40에 먹었어도 칩은 여전히 80이어야 한다
        MvcResult presets = mvc.perform(get("/presets").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        int ramenPoints = -1;
        for (JsonNode p : json(presets).get("presets")) {
            if ("라면".equals(p.get("name").asString())) {
                ramenPoints = p.get("points").asInt();
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(80, ramenPoints,
                "칩 가격은 합의값이 아니라 original이어야 한다");
    }

    @Test
    @Order(6)
    @DisplayName("★ 이력 칩으로 담아도 original 그대로 들어간다")
    void addingFromHistoryChipUsesOriginal() throws Exception {
        MvcResult presets = mvc.perform(get("/presets").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        String historyId = null;
        for (JsonNode p : json(presets).get("presets")) {
            if ("라면".equals(p.get("name").asString())) {
                historyId = p.get("id").asString();
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(historyId);

        mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"" + historyId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.original.name").value("라면"))
                .andExpect(jsonPath("$.original.points").value(80))
                .andExpect(jsonPath("$.original.unit").value("1봉지"))
                // 새 항목은 흥정 이력을 물려받지 않는다
                .andExpect(jsonPath("$.adjusted").doesNotExist())
                .andExpect(jsonPath("$.effective.points").value(80));
    }

    @Test
    @Order(7)
    @DisplayName("남의 이력 프리셋은 담을 수 없다")
    void cannotUseSomeoneElsesHistoryChip() throws Exception {
        MvcResult otherLogin = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"presets-intruder","deviceId":"d-int"}
                                """))
                .andExpect(status().isOk()).andReturn();
        String otherToken = json(otherLogin).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(post("/challenges/" + json(created).get("id").asText() + "/budget")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"budget":225}
                                """))
                .andExpect(status().isOk());

        // 앞 사용자의 이력 칩 id를 그대로 써 본다
        MvcResult presets = mvc.perform(get("/presets").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        String victimChip = null;
        for (JsonNode p : json(presets).get("presets")) {
            if (p.get("id").asString().startsWith("pst_hist_")) {
                victimChip = p.get("id").asString();
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(victimChip);

        mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"" + victimChip + "\"}"))
                .andExpect(status().isNotFound());
    }
}
