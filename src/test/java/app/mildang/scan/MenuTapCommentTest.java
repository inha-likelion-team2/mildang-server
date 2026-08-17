package app.mildang.scan;

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

/**
 * 화면 4b — 하단 메뉴 목록에서 항목을 탭하면 상단 노란 메모가 그 메뉴로 바뀐다.
 * 코멘트는 메뉴마다 달라야 한다: 추천 메뉴 것을 그대로 쓰면 «냉면을 고르면 안 좋다»는 말이
 * 냉면을 골랐을 때도 남아 앞뒤가 안 맞는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MenuTapCommentTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String scanId;

    private JsonNode json(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String auth() {
        return "Bearer " + token;
    }

    private static byte[] jpeg() throws Exception {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private MvcResult tap(String menuId) throws Exception {
        return mvc.perform(post("/scans/" + scanId + "/menus/" + menuId + "/comment")
                        .header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menuId").value(menuId))
                .andReturn();
    }

    @Test
    @Order(1)
    @DisplayName("준비 — W1 확정 후 메뉴판 스캔")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"menu-tap-user","deviceId":"d-tap"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        String challengeId = json(created).get("id").asText();
        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"budget":225}
                                """))
                .andExpect(status().isOk());

        MvcResult scan = mvc.perform(multipart("/scans")
                        .file(new MockMultipartFile("image", "menu.jpg", "image/jpeg", jpeg()))
                        .param("challengeId", challengeId)
                        .header("Authorization", auth()))
                .andExpect(status().isCreated()).andReturn();
        scanId = json(scan).get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("★ 탭한 메뉴의 메모를 준다 — 이름·가격·근거·차감 후 잔액")
    void tapFillsTheMemo() throws Exception {
        JsonNode memo = json(tap("mnu_3")); // 제육볶음 15
        org.assertj.core.api.Assertions.assertThat(memo.get("name").asText()).isEqualTo("제육볶음");
        org.assertj.core.api.Assertions.assertThat(memo.get("points").asInt()).isEqualTo(15);
        org.assertj.core.api.Assertions.assertThat(memo.get("basis").asText()).isNotBlank();
        // 「탭해서 15 차감」 옆에 보여줄 값
        org.assertj.core.api.Assertions.assertThat(memo.get("balanceAfter").asInt()).isEqualTo(225 - 15);
    }

    @Test
    @Order(3)
    @DisplayName("★ 메뉴마다 코멘트가 다르다 — 자기 자신을 «고르지 말라»고 하지 않는다")
    void commentDiffersPerMenu() throws Exception {
        String cheap = json(tap("mnu_3")).get("comment").asText("");   // 제육볶음 15
        String pricey = json(tap("mnu_4")).get("comment").asText("");  // 냉면 40

        org.assertj.core.api.Assertions.assertThat(cheap).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(pricey).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(pricey)
                .as("냉면을 탭했는데 제육볶음 코멘트가 그대로 나오면 안 된다").isNotEqualTo(cheap);
        // 비교 대상은 자기 자신이 아니어야 한다
        org.assertj.core.api.Assertions.assertThat(pricey).doesNotContain("냉면(40)을 고르면");
    }

    @Test
    @Order(4)
    @DisplayName("같은 메뉴를 다시 탭하면 만들어둔 코멘트를 그대로 쓴다 (AI 재호출 없음)")
    void reusesCachedComment() throws Exception {
        String first = json(tap("mnu_3")).get("comment").asText("");
        String second = json(tap("mnu_3")).get("comment").asText("");
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    @Order(5)
    @DisplayName("가격을 수정하면 코멘트를 다시 만든다 — 낡은 문구가 남지 않는다")
    void refreshesCommentAfterPriceEdit() throws Exception {
        String before = json(tap("mnu_3")).get("comment").asText("");

        mvc.perform(patch("/scans/" + scanId + "/menus/mnu_3")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":60}"))
                .andExpect(status().isOk());

        JsonNode memo = json(tap("mnu_3"));
        org.assertj.core.api.Assertions.assertThat(memo.get("points").asInt()).isEqualTo(60);
        org.assertj.core.api.Assertions.assertThat(memo.get("balanceAfter").asInt()).isEqualTo(225 - 60);
        org.assertj.core.api.Assertions.assertThat(memo.get("comment").asText(""))
                .as("15원짜리 시절 코멘트가 그대로 남으면 안 된다").isNotEqualTo(before);
    }

    @Test
    @Order(6)
    @DisplayName("없는 메뉴를 탭하면 404")
    void unknownMenu() throws Exception {
        mvc.perform(post("/scans/" + scanId + "/menus/mnu_99/comment")
                        .header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }
}
