package app.mildang.scan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 스캔(4a→4b)·리포트(7)·초대 E2E — Fake AI 기준 (명세 §5.3·§9·§12.2) */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScanReportFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String challengeId;
    String scanId;
    String inviteCode;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private static byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    @Test
    @Order(1)
    @DisplayName("준비 — 로그인 + W1 확정")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"scan-e2e-user","deviceId":"d-s1"}
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
    @DisplayName("스캔 — 싼 순 정렬·백엔드 추천(잔액÷끼수)·코멘트 비교 메뉴 언급 (§5.3)")
    void scan() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "menu.jpg", "image/jpeg", jpeg());
        MvcResult result = mvc.perform(multipart("/scans")
                        .file(image)
                        .param("challengeId", challengeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.place").value("김밥천국 성수점"))
                .andExpect(jsonPath("$.menus.length()").value(5))
                .andExpect(jsonPath("$.menus[0].name").value("삼겹살"))
                .andExpect(jsonPath("$.menus[0].points").value(0))
                .andExpect(jsonPath("$.menus[4].name").value("칼국수"))
                // 잔액 225 · 남은 끼수 6 → 상한 37 → 이하 중 최고가 = 제육볶음 15
                .andExpect(jsonPath("$.recommendation.points").value(15))
                .andReturn();
        scanId = json(result).get("id").asText();
        String comment = json(result).get("recommendation").get("comment").asText();
        org.assertj.core.api.Assertions.assertThat(comment).contains("냉면"); // 비교 대상 = 상한 초과 중 최저가

        // 재진입 조회 동일 구조
        mvc.perform(get("/scans/" + scanId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menus.length()").value(5));
    }

    @Test
    @Order(3)
    @DisplayName("가격 수정(§5.5) → 수정 후 생성한 항목에 반영 → 기록")
    void patchAndCreateItem() throws Exception {
        // 제육볶음 = 추출 순서 3번째 → mnu_3
        mvc.perform(patch("/scans/" + scanId + "/menus/mnu_3")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(25))
                .andExpect(jsonPath("$.pm").value(0))
                .andExpect(jsonPath("$.confidence").value("CERTAIN"))
                .andExpect(jsonPath("$.edited").value(true));

        MvcResult item = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"scanId\":\"" + scanId + "\",\"menuId\":\"mnu_3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source.type").value("IMAGE"))
                .andExpect(jsonPath("$.original.name").value("제육볶음"))
                .andExpect(jsonPath("$.original.points").value(25))
                .andReturn();

        mvc.perform(post("/items/" + json(item).get("id").asText() + "/record")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.balance").value(200));
    }

    @Test
    @Order(4)
    @DisplayName("완주 리포트 — 통계·발견(스무딩 비율)·흥정 하이라이트·재대결 CTA (§9.1)")
    void report() throws Exception {
        // 완주 상태 시드 (심사위원3 시나리오)
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"demo-judge-03","deviceId":"d-j3"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();

        MvcResult seeded = mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andReturn();
        String completedId = json(seeded).get("challengeId").asText();

        MvcResult report = mvc.perform(get("/challenges/" + completedId + "/report")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challenge.label").value("1주 챌린지 · 완주"))
                .andExpect(jsonPath("$.title").value("당신의 몸이 쓴 리포트"))
                .andExpect(jsonPath("$.stats[0].key").value("TOTAL_SPENT"))
                .andExpect(jsonPath("$.stats[0].value").value("80"))
                .andExpect(jsonPath("$.stats[1].value").value("-5"))
                .andExpect(jsonPath("$.finding.available").value(true))
                .andExpect(jsonPath("$.finding.sample.answeredDays").value(6))
                .andExpect(jsonPath("$.haggleHighlight.totalSaved").value(40))
                .andExpect(jsonPath("$.haggleHighlight.best.menu").value("라면"))
                .andExpect(jsonPath("$.nextChallenge.optionKey").value("HARD"))
                .andExpect(jsonPath("$.nextChallenge.suggestedBudget").value(75))
                .andExpect(jsonPath("$.nextChallenge.ctaLabel").value("재대결 받기 · 이번엔 75"))
                .andReturn();
        String headline = json(report).get("finding").get("headline").asText();
        double ratio = json(report).get("finding").get("metric").get("ratio").asDouble();
        org.assertj.core.api.Assertions.assertThat(headline).contains(String.valueOf(ratio));
    }

    @Test
    @Order(5)
    @DisplayName("공유 카드 → 초대 랜딩(무인증) (§9.2-3)")
    void shareAndInvite() throws Exception {
        MvcResult seeded = mvc.perform(post("/demo/seed")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scenario\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andReturn();
        String completedId = json(seeded).get("challengeId").asText();

        MvcResult card = mvc.perform(post("/challenges/" + completedId + "/report/share-card")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mentions\":[],\"format\":\"PNG\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mocked").value(true))
                .andExpect(jsonPath("$.width").value(1080))
                .andExpect(jsonPath("$.hashtag").value("#밀가루흥정챌린지"))
                .andReturn();
        String deepLink = json(card).get("deepLink").asText();
        inviteCode = deepLink.substring(deepLink.lastIndexOf('/') + 1);

        // 무인증 진입
        mvc.perform(get("/invites/" + inviteCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviterNickname").value("심사위원3"))
                .andExpect(jsonPath("$.ctaLabel").value("도전 받기"));
    }
}
