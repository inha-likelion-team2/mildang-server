package app.mildang.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
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
 * 완주 리포트 데모 데이터 — 계정마다 다른 판이 나오는지, advance-day로 완주시킨 시드에도
 * 「내 몸의 변화」가 채워지는지 (FE 제보 2026-08-20 ①·②).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompletionReportSeedTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    private String token(String idToken) throws Exception {
        String body = mvc.perform(post("/auth/social").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"KAKAO\",\"idToken\":\"" + idToken
                                + "\",\"deviceId\":\"d-" + idToken + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("accessToken").asString();
    }

    private String seed(String token, String scenario) throws Exception {
        String body = mvc.perform(post("/demo/seed").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"" + scenario + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body).get("challengeId").asString();
    }

    /** 「마지막 날까지 간 뒤 한 번 더」 — 시연 시각과 무관하게 이 횟수로 완주해야 한다 */
    private void advance(String token, int days) throws Exception {
        mvc.perform(post("/demo/advance-day").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":" + days + "}"))
                .andExpect(status().isOk());
    }

    /**
     * 시드가 남긴 데이터는 반드시 지운다 — `PREPAID_CONVERT` 같은 배치는 계정을 가리지 않아서,
     * 남겨둔 선차감 하나가 다른 테스트의 `converted` 카운트를 늘린다.
     */
    private void reset(String token) throws Exception {
        mvc.perform(post("/demo/reset").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    private JsonNode report(String token, String challengeId) throws Exception {
        String body = mvc.perform(get("/challenges/" + challengeId + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    private static Map<String, JsonNode> bodyChanges(JsonNode report) {
        Map<String, JsonNode> byKey = new HashMap<>();
        report.get("completion").get("bodyChanges").forEach(c -> byKey.put(c.get("key").asString(), c));
        return byKey;
    }

    @Test
    @DisplayName("judge-03 COMPLETED — W1 성공: 94% · 85/80 · 58kg → 54kg")
    void judge03IsW1Success() throws Exception {
        String t = token("demo-judge-03");
        JsonNode r = report(t, seed(t, "COMPLETED"));
        JsonNode completion = r.get("completion");

        assertThat(completion.get("periodLabel").asString()).isEqualTo("1주 챌린지 완주 🎉");
        assertThat(completion.get("headline").asString()).isEqualTo("이번 판 밀당 성공 !");
        assertThat(completion.get("usedPercent").asInt()).isEqualTo(94);
        assertThat(completion.get("totalBudget").asInt()).isEqualTo(85);
        assertThat(completion.get("spent").asInt()).isEqualTo(80);
        assertThat(completion.get("leftover").asInt()).isEqualTo(5);
        Map<String, JsonNode> changes = bodyChanges(r);
        assertThat(changes.get("WEIGHT").get("value").asString()).isEqualTo("58kg → 54kg");
        assertThat(changes.get("WEIGHT").get("note").asString()).isEqualTo("4kg 줄었어요");
        assertThat(changes.get("BLOAT").get("value").asString()).isEqualTo("보통 → 좋음");
        assertThat(changes.get("SKIN").get("value").asString()).isEqualTo("0회 → 0회");
        assertThat(changes.get("DROWSY").get("value").asString()).isEqualTo("33% 개선");
        assertThat(r.get("finding").get("available").asBoolean()).isTrue();
        assertThat(r.get("finding").get("metric").get("ratio").asDouble()).isEqualTo(9.0);
        reset(t);
    }

    @Test
    @DisplayName("judge-04 COMPLETED — W4 완주: 65% · 340/220 · 72kg → 68.5kg")
    void judge04IsW4() throws Exception {
        String t = token("demo-judge-04");
        JsonNode r = report(t, seed(t, "COMPLETED"));
        JsonNode completion = r.get("completion");

        assertThat(r.get("challenge").get("period").asString()).isEqualTo("W4");
        assertThat(completion.get("periodLabel").asString()).isEqualTo("4주 챌린지 완주 🎉");
        assertThat(completion.get("usedPercent").asInt()).isEqualTo(65);
        assertThat(completion.get("totalBudget").asInt()).isEqualTo(340);
        assertThat(completion.get("spent").asInt()).isEqualTo(220);
        assertThat(completion.get("leftover").asInt()).isEqualTo(120);

        Map<String, JsonNode> changes = bodyChanges(r);
        assertThat(changes.get("WEIGHT").get("value").asString()).isEqualTo("72kg → 68.5kg");
        assertThat(changes.get("BLOAT").get("value").asString()).isEqualTo("보통 → 좋음");
        assertThat(changes.get("SKIN").get("value").asString()).isEqualTo("3회 → 1회");
        assertThat(changes.values()).allSatisfy(c -> assertThat(c.get("value").isNull()).isFalse());

        assertThat(r.get("finding").get("available").asBoolean()).isTrue();
        assertThat(r.get("finding").get("metric").get("conditionKey").asString()).isEqualTo("BLOAT");
        assertThat(r.get("finding").get("metric").get("ratio").asDouble()).isEqualTo(7.0);
        assertThat(r.get("finding").get("sample").get("answeredDays").asInt()).isEqualTo(14);
        reset(t);
    }

    @Test
    @DisplayName("judge-05 COMPLETED — W2 예산 초과: usedPercent 112 · leftover −20 · 체중 증가")
    void judge05IsW2OverBudget() throws Exception {
        String t = token("demo-judge-05");
        JsonNode r = report(t, seed(t, "COMPLETED"));
        JsonNode completion = r.get("completion");

        assertThat(r.get("challenge").get("period").asString()).isEqualTo("W2");
        assertThat(completion.get("usedPercent").asInt())
                .as("초과 완주 화면(usedPercent > 100)을 실서버로 확인할 수 있어야 한다")
                .isEqualTo(112);
        assertThat(completion.get("totalBudget").asInt()).isEqualTo(170);
        assertThat(completion.get("spent").asInt()).isEqualTo(190);
        assertThat(completion.get("leftover").asInt()).isEqualTo(-20);
        // 톤 규칙 — 초과해도 판정하지 않는다
        assertThat(completion.get("headline").asString()).isEqualTo("이번 판, 끝까지 갔어요 !");
        assertThat(completion.get("summaryLine").asString()).isEqualTo("처음 170에서 시작해, 0을 남기고 완주했어요!");
        // 예산 대비는 leftover와 같은 부호 — 초과했으니 음수
        assertThat(r.get("stats").get(1).get("value").asString()).isEqualTo("-20");

        Map<String, JsonNode> changes = bodyChanges(r);
        assertThat(changes.get("WEIGHT").get("value").asString()).isEqualTo("61kg → 61.5kg");
        assertThat(changes.get("WEIGHT").get("note").asString()).isEqualTo("0.5kg 늘었어요");
        assertThat(changes.values()).allSatisfy(c -> assertThat(c.get("value").isNull()).isFalse());
        assertThat(r.get("finding").get("metric").get("ratio").asDouble()).isEqualTo(4.7);
        reset(t);
    }

    @Test
    @DisplayName("표에 없는 계정도 셋 중 하나로 고정 배정 — 같은 계정은 몇 번 시드해도 같은 판")
    void unlistedAccountIsStable() throws Exception {
        String t = token("demo-judge-99");
        JsonNode first = report(t, seed(t, "COMPLETED"));
        JsonNode second = report(t, seed(t, "COMPLETED"));
        assertThat(second.get("completion").get("usedPercent").asInt())
                .isEqualTo(first.get("completion").get("usedPercent").asInt());
        assertThat(second.get("challenge").get("period").asString())
                .isEqualTo(first.get("challenge").get("period").asString());
        reset(t);
    }

    @Test
    @DisplayName("W2_DAY8을 완주시키면 「내 몸의 변화」 4칸이 다 찬다 — 65kg → 63kg")
    void w2Day8CompletesWithBodyChanges() throws Exception {
        String t = token("demo-judge-w2");
        String id = seed(t, "W2_DAY8");
        advance(t, 6);   // dayIndex 14 (마지막 날) — 아직 ACTIVE
        advance(t, 1);   // 마지막 날을 넘겨 COMPLETED

        JsonNode r = report(t, id);
        assertThat(r.get("completion").get("usedPercent").asInt()).isEqualTo(47);
        assertThat(r.get("completion").get("totalBudget").asInt()).isEqualTo(170);
        assertThat(r.get("completion").get("spent").asInt()).isEqualTo(80);

        Map<String, JsonNode> changes = bodyChanges(r);
        assertThat(changes.get("WEIGHT").get("value").asString()).isEqualTo("65kg → 63kg");
        assertThat(changes.get("BLOAT").get("value").asString()).isEqualTo("나쁨 → 좋음");
        assertThat(changes.get("SKIN").get("value").asString()).isEqualTo("2회 → 0회");
        assertThat(changes.values())
                .as("네 칸이 전부 「기록이 모자라요」로 비면 데모 화면이 빈 것처럼 보인다")
                .allSatisfy(c -> assertThat(c.get("value").isNull()).isFalse());

        assertThat(r.get("finding").get("available").asBoolean()).isTrue();
        assertThat(r.get("finding").get("sample").get("answeredDays").asInt()).isEqualTo(6);
        assertThat(r.get("finding").get("metric").get("conditionKey").asString()).isEqualTo("BLOAT");
        assertThat(r.get("finding").get("metric").get("ratio").asDouble()).isEqualTo(3.0);
        reset(t);
    }

    @Test
    @DisplayName("W4_DAY12를 완주시키면 「내 몸의 변화」 4칸이 다 찬다 — 75kg → 73kg")
    void w4Day12CompletesWithBodyChanges() throws Exception {
        String t = token("demo-judge-w4");
        String id = seed(t, "W4_DAY12");
        advance(t, 16);  // dayIndex 28 (마지막 날)
        advance(t, 1);   // COMPLETED

        JsonNode r = report(t, id);
        assertThat(r.get("completion").get("usedPercent").asInt()).isEqualTo(18);
        assertThat(r.get("completion").get("totalBudget").asInt()).isEqualTo(340);
        assertThat(r.get("completion").get("spent").asInt()).isEqualTo(60);

        Map<String, JsonNode> changes = bodyChanges(r);
        assertThat(changes.get("WEIGHT").get("value").asString()).isEqualTo("75kg → 73kg");
        assertThat(changes.get("BLOAT").get("value").asString()).isEqualTo("보통 → 좋음");
        assertThat(changes.values()).allSatisfy(c -> assertThat(c.get("value").isNull()).isFalse());

        assertThat(r.get("finding").get("available").asBoolean()).isTrue();
        assertThat(r.get("finding").get("sample").get("answeredDays").asInt()).isEqualTo(8);
        assertThat(r.get("finding").get("metric").get("ratio").asDouble()).isEqualTo(4.0);
        reset(t);
    }

    @Test
    @DisplayName("VS_BUDGET은 leftover와 같은 값·같은 부호 — 5 아끼고 완주하면 +5")
    void vsBudgetKeepsLeftoverSign() throws Exception {
        String t = token("demo-judge-03");
        JsonNode r = report(t, seed(t, "COMPLETED"));
        JsonNode vsBudget = r.get("stats").get(1);
        assertThat(vsBudget.get("key").asString()).isEqualTo("VS_BUDGET");
        assertThat(vsBudget.get("value").asString())
                .as("5를 아꼈는데 -5로 찍히면 공유 카드에서 «초과»로 읽힌다 (FE 제보 2026-08-20 ③)")
                .isEqualTo("+5");
        assertThat(vsBudget.get("value").asString())
                .isEqualTo("+" + r.get("completion").get("leftover").asInt());
        reset(t);
    }
}
