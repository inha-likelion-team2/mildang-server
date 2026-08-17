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
 * 화면 «기록 보기» — 캘린더로 날짜를 고르고 그날 먹은 것만 본다.
 * 날짜 경계는 자정이 아니라 05:00 KST(LogicalDate)이므로 새벽 3시 라면은 «어제»에 남는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RecordsByDateTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String today;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String auth() {
        return "Bearer " + token;
    }

    /** 항목 생성 → 기록까지 한 번에 (그날 먹은 것 한 건). 프리셋을 쓰면 AI 없이 값이 고정된다 */
    private void eat(String presetId) throws Exception {
        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"presetId\":\"" + presetId + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        mvc.perform(post("/items/" + json(item).get("id").asText() + "/record")
                        .header("Authorization", auth()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(1)
    @DisplayName("준비 — W1 확정 후 오늘 두 건 기록")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"records-user","deviceId":"d-rec"}
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

        today = json(mvc.perform(get("/challenges/current").header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn()).get("today").get("date").asText();

        eat("pst_ramen");
        eat("pst_bread");
    }

    @Test
    @Order(2)
    @DisplayName("★ 날짜를 주면 그날치만 오고 day에 건수·합계가 붙는다")
    void filtersByDate() throws Exception {
        MvcResult result = mvc.perform(get("/items")
                        .param("date", today)
                        .param("status", "RECORDED")
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.day.date").value(today))
                .andExpect(jsonPath("$.day.count").value(2))
                .andReturn();

        // day.totalPoints는 그날 항목들의 effective 합과 같아야 한다
        JsonNode body = json(result);
        int sum = 0;
        for (JsonNode item : body.get("items")) {
            sum += item.get("effective").get("points").asInt();
        }
        org.junit.jupiter.api.Assertions.assertEquals(sum, body.get("day").get("totalPoints").asInt());
    }

    @Test
    @Order(3)
    @DisplayName("기록이 없는 날은 빈 목록 — 404가 아니다")
    void emptyDayIsNotAnError() throws Exception {
        String past = java.time.LocalDate.parse(today).minusDays(3).toString();
        mvc.perform(get("/items").param("date", past).header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.day.count").value(0))
                .andExpect(jsonPath("$.day.totalPoints").value(0));
    }

    @Test
    @Order(4)
    @DisplayName("날짜를 안 주면 예전 그대로 — day 없이 전체 목록")
    void withoutDateNothingChanges() throws Exception {
        mvc.perform(get("/items").param("status", "RECORDED").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.day").doesNotExist())
                .andExpect(jsonPath("$.summary").exists());
    }

    @Test
    @Order(5)
    @DisplayName("★ 캘린더 — 그 달에 기록이 있는 날만 온다")
    void recordedDaysForCalendar() throws Exception {
        String month = today.substring(0, 7);
        mvc.perform(get("/items/dates").param("month", month).header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(month))
                .andExpect(jsonPath("$.days.length()").value(1)) // 기록한 날은 오늘 하루
                .andExpect(jsonPath("$.days[0].date").value(today))
                .andExpect(jsonPath("$.days[0].count").value(2));

        // month를 생략하면 이번 달
        mvc.perform(get("/items/dates").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(month));
    }

    @Test
    @Order(6)
    @DisplayName("형식이 틀린 날짜는 400 — 안내 문구를 준다")
    void rejectsMalformed() throws Exception {
        mvc.perform(get("/items").param("date", "8/16").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.field").value("date"));

        mvc.perform(get("/items/dates").param("month", "2026-8").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("month"));
    }
}
