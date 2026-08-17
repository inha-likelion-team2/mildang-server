package app.mildang.challenge;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.mildang.common.model.Weekday;
import app.mildang.common.time.LogicalDate;
import java.time.Instant;
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
 * 메인 대시보드의 두 카드 (확정 와이어프레임 193:1220).
 *
 * <p>「오늘의 알림」은 <b>오늘 잡혀 있는 약속</b>이다 — 밀당이 말풍선의 AI 팁과는 다른 자리다
 * (2026-08-17 확인). 오른쪽 「미리 약속을 잡았나요?」는 3a로 가는 버튼이라 API가 따로 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardCardsTest {

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

    /** 오늘 요일로 약속을 하나 잡는다 (프리셋 → PROMISE 항목) */
    private String promiseToday(String presetId) throws Exception {
        Weekday today = Weekday.of(LogicalDate.of(Instant.now()).getDayOfWeek());
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROMISE\",\"presetId\":\"" + presetId
                                + "\",\"weekday\":\"" + today.name() + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json(item).get("id").asText();
    }

    @Test
    @Order(1)
    @DisplayName("준비 — W1 확정")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"dash-cards","deviceId":"d-dash"}
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
    @DisplayName("★ 약속이 없으면 카드는 남되 «없어요»로 — 판정하지 않는 어투")
    void emptyNoticeStillHasCard() throws Exception {
        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayNotice").exists())
                .andExpect(jsonPath("$.todayNotice.promises.length()").value(0))
                .andExpect(jsonPath("$.todayNotice.text").value("오늘 잡힌 약속은 없어요"));
    }

    @Test
    @Order(3)
    @DisplayName("★ 오늘 잡힌 약속이 카드에 뜬다")
    void showsTodayPromise() throws Exception {
        promiseToday("pst_chicken");

        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayNotice.promises.length()").value(1))
                .andExpect(jsonPath("$.todayNotice.promises[0].name").value("치킨"))
                .andExpect(jsonPath("$.todayNotice.promises[0].points").value(70))
                .andExpect(jsonPath("$.todayNotice.promises[0].prepaid").value(false))
                .andExpect(jsonPath("$.todayNotice.text").value("오늘 치킨 약속이 있어요"));
    }

    @Test
    @Order(4)
    @DisplayName("★ 선차감한 약속도 오늘 카드에 남는다 — prepaid로 구분해서")
    void prepaidPromiseStaysOnTheCard() throws Exception {
        String id = promiseToday("pst_ramen");
        mvc.perform(post("/items/" + id + "/prepay").header("Authorization", auth()))
                .andExpect(status().isOk());

        MvcResult result = mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayNotice.promises.length()").value(2))
                .andExpect(jsonPath("$.todayNotice.text").value("오늘 약속이 2건 있어요"))
                .andReturn();

        JsonNode promises = json(result).get("todayNotice").get("promises");
        boolean ramenPrepaid = false;
        for (JsonNode p : promises) {
            if ("라면".equals(p.get("name").asString())) {
                ramenPrepaid = p.get("prepaid").asBoolean();
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(ramenPrepaid, "선차감한 약속은 prepaid=true여야 한다");
    }

    @Test
    @Order(5)
    @DisplayName("다른 요일 약속은 오늘 카드에 오지 않는다")
    void otherWeekdayIsNotToday() throws Exception {
        Weekday today = Weekday.of(LogicalDate.of(Instant.now()).getDayOfWeek());
        Weekday other = Weekday.values()[(today.ordinal() + 3) % 7];

        mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"PROMISE\",\"presetId\":\"pst_tteok\",\"weekday\":\""
                                + other.name() + "\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                // 앞 테스트의 2건 그대로 — 늘지 않는다
                .andExpect(jsonPath("$.todayNotice.promises.length()").value(2));
    }

    @Test
    @Order(6)
    @DisplayName("★ 팁(밀당이 말풍선)은 알림과 다른 자리라 그대로 있다")
    void tipIsSeparate() throws Exception {
        mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tip").exists())
                .andExpect(jsonPath("$.tip.text").isNotEmpty());
    }
}
