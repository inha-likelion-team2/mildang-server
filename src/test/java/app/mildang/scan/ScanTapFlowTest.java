package app.mildang.scan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 화면 4b 스캔 결과 — 찍으면 아래 목록이 뜨고, <b>행을 탭할 때마다</b> 위 노란 카드가 그 메뉴로 바뀐다.
 * 탭 = 그 메뉴의 코멘트 생성 (확정 193:1556).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScanTapFlowTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String challengeId;
    String scanId;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String auth() {
        return "Bearer " + token;
    }

    /** 서버가 ImageIO로 다시 인코딩하므로 진짜 PNG여야 한다 (아무 바이트나 주면 400) */
    private static byte[] pngBytes() throws Exception {
        java.awt.image.BufferedImage image =
                new java.awt.image.BufferedImage(60, 60, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    @Order(1)
    @DisplayName("준비 — W1 확정 후 메뉴판 스캔")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"scan-tap","deviceId":"d-st"}
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
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"budget":225}
                                """))
                .andExpect(status().isOk());

        MvcResult scan = mvc.perform(multipart("/scans")
                        .file(new MockMultipartFile("image", "menu.png", "image/png", pngBytes()))
                        .param("challengeId", challengeId)
                        .header("Authorization", auth()))
                .andExpect(status().isCreated())
                // 가게명·스캔 시각은 화면 상단에 그대로 쓰인다
                .andExpect(jsonPath("$.place").isNotEmpty())
                .andExpect(jsonPath("$.scannedAt").isNotEmpty())
                .andExpect(jsonPath("$.menus.length()").value(org.hamcrest.Matchers.greaterThan(1)))
                .andReturn();
        scanId = json(scan).get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("★ 목록은 밀가루가 낮은 순 — 화면 라벨 「밀가루 크기가 낮은 순」")
    void cheapestFirst() throws Exception {
        MvcResult scan = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/scans/" + scanId).header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();

        int previous = -1;
        for (JsonNode menu : json(scan).get("menus")) {
            int points = menu.get("points").asInt();
            org.junit.jupiter.api.Assertions.assertTrue(points >= previous,
                    "포인트 오름차순이어야 한다: " + points + " < " + previous);
            previous = points;
        }
    }

    @Test
    @Order(3)
    @DisplayName("★ 행을 탭하면 그 메뉴의 코멘트가 온다 — 메뉴마다 다르다")
    void tappingEachRowGivesItsOwnComment() throws Exception {
        MvcResult scan = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/scans/" + scanId).header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        JsonNode menus = json(scan).get("menus");

        String firstComment = null;
        String lastComment = null;
        for (int i = 0; i < menus.size(); i++) {
            String menuId = menus.get(i).get("id").asString();
            MvcResult result = mvc.perform(post("/scans/" + scanId + "/menus/" + menuId + "/comment")
                            .header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.menuId").value(menuId))
                    .andExpect(jsonPath("$.comment").isNotEmpty())
                    // 상단 카드가 「기록하면 잔액 …」을 그리려면 이 값이 필요하다
                    .andExpect(jsonPath("$.balanceAfter").isNumber())
                    .andReturn();
            if (i == 0) {
                firstComment = json(result).get("comment").asString();
            }
            lastComment = json(result).get("comment").asString();
        }
        org.junit.jupiter.api.Assertions.assertNotEquals(firstComment, lastComment,
                "메뉴가 다르면 코멘트도 달라야 한다 — 같으면 탭이 의미가 없다");
    }

    @Test
    @Order(4)
    @DisplayName("같은 메뉴를 다시 탭하면 같은 코멘트 — 매번 AI를 다시 부르지 않는다")
    void secondTapIsCached() throws Exception {
        MvcResult scan = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/scans/" + scanId).header("Authorization", auth()))
                .andExpect(status().isOk()).andReturn();
        String menuId = json(scan).get("menus").get(0).get("id").asString();

        MvcResult first = mvc.perform(post("/scans/" + scanId + "/menus/" + menuId + "/comment")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn();
        MvcResult again = mvc.perform(post("/scans/" + scanId + "/menus/" + menuId + "/comment")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andReturn();

        org.junit.jupiter.api.Assertions.assertEquals(
                json(first).get("comment").asString(), json(again).get("comment").asString());
    }

    @Test
    @Order(5)
    @DisplayName("★ challengeId를 빼먹으면 500이 아니라 안내되는 400")
    void missingChallengeIdIsGuided() throws Exception {
        mvc.perform(multipart("/scans")
                        .file(new MockMultipartFile("image", "menu.png", "image/png", pngBytes()))
                        .header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.field").value("challengeId"));
    }
}
