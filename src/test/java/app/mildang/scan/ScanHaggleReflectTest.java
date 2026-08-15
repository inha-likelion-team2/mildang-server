package app.mildang.scan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
 * 스캔 메뉴 흥정 결과가 4b 화면·기록에 반영되는지 — 팀 이슈 #1 재현.
 * 증상: 스캔 → 밀당으로 낮춘 값이 목록에 안 보이고, 기록하면 원래값이 빠져나갔다.
 * 원인: 같은 메뉴로 POST /items를 부를 때마다 새 항목이 생겨 흥정한 항목이 버려졌고,
 * MenuRow에 항목 연결이 없어 4b가 조정값을 알 수 없었다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ScanHaggleReflectTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper om;

    String token;
    String scanId;
    String itemId;

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
    @DisplayName("준비 — 로그인·W1 확정·스캔")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"scan-haggle-user","deviceId":"d-sh1"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();

        MvcResult created = mvc.perform(post("/challenges")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"W1\",\"paymentId\":null}"))
                .andExpect(status().isCreated()).andReturn();
        String challengeId = json(created).get("id").asText();

        mvc.perform(post("/challenges/" + challengeId + "/budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"survey":{"noodle":"2-3","bread":"0-1","snack":"4+"},"optionKey":"AS_IS","budget":225}
                                """))
                .andExpect(status().isOk());

        MvcResult scan = mvc.perform(multipart("/scans")
                        .file(new MockMultipartFile("image", "menu.jpg", "image/jpeg", jpeg()))
                        .param("challengeId", challengeId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                // 아직 항목이 없는 행은 item = null
                .andExpect(jsonPath("$.menus[0].item").isEmpty())
                .andReturn();
        scanId = json(scan).get("id").asText();
    }

    @Test
    @Order(2)
    @DisplayName("같은 스캔 메뉴로 재요청 — 새 항목이 아니라 기존 항목을 돌려준다")
    void reusesLiveItem() throws Exception {
        MvcResult first = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"scanId\":\"" + scanId + "\",\"menuId\":\"mnu_3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.original.name").value("제육볶음"))
                .andExpect(jsonPath("$.source.scanId").value(scanId))
                .andExpect(jsonPath("$.source.menuId").value("mnu_3"))
                .andReturn();
        itemId = json(first).get("id").asText();

        mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"scanId\":\"" + scanId + "\",\"menuId\":\"mnu_3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(itemId));
    }

    @Test
    @Order(3)
    @DisplayName("밀당으로 낮춘 값이 4b 행에 그대로 보인다")
    void scanRowShowsAdjusted() throws Exception {
        MvcResult opened = mvc.perform(post("/haggles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":\"" + itemId + "\",\"entryPoint\":\"SCAN\"}"))
                .andExpect(status().isCreated()).andReturn();
        String haggleId = json(opened).get("id").asText();

        mvc.perform(post("/haggles/" + haggleId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"반만 먹을게\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agreed.points").value(8)); // 제육볶음 15의 절반(내림)

        mvc.perform(post("/haggles/" + haggleId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.effective.points").value(8));

        // ★ 4b 복귀 — 행이 조정값을 그대로 보여준다 (§5.3)
        mvc.perform(get("/scans/" + scanId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menus[2].id").value("mnu_3"))
                .andExpect(jsonPath("$.menus[2].points").value(15))   // 메뉴판 원값은 그대로
                .andExpect(jsonPath("$.menus[2].item.id").value(itemId))
                .andExpect(jsonPath("$.menus[2].item.points").value(8))
                .andExpect(jsonPath("$.menus[2].item.haggled").value(true))
                .andExpect(jsonPath("$.menus[2].item.status").value("HAGGLED"));
    }

    @Test
    @Order(4)
    @DisplayName("흥정 후 기록 — 원래값 15이 아니라 합의값 8만 빠진다")
    void recordsAgreedValue() throws Exception {
        // FE가 흥정 사실을 모른 채 다시 항목 생성을 시도해도 같은 항목이어야 한다
        MvcResult again = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"scanId\":\"" + scanId + "\",\"menuId\":\"mnu_3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(itemId))
                .andExpect(jsonPath("$.effective.points").value(8))
                .andReturn();

        mvc.perform(post("/items/" + json(again).get("id").asText() + "/record")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.item.effective.points").value(8))
                .andExpect(jsonPath("$.budget.balance").value(217))
                .andExpect(jsonPath("$.budget.spent").value(8));

        // 확정된 항목은 행에서 빠진다 — 다시 담으면 새 항목
        mvc.perform(get("/scans/" + scanId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.menus[2].item").isEmpty());

        MvcResult fresh = mvc.perform(post("/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"MEAL\",\"scanId\":\"" + scanId + "\",\"menuId\":\"mnu_3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.effective.points").value(15))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(json(fresh).get("id").asText()).isNotEqualTo(itemId);

        // 삭제(이슈 #3) — 미확정 항목은 지워지고, 지운 뒤 행도 비워진다
        mvc.perform(delete("/items/" + json(fresh).get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/scans/" + scanId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.menus[2].item").isEmpty());
    }

    @Test
    @Order(5)
    @DisplayName("메인 화면 today — 오늘 기록한 메뉴와 값이 실린다 (이슈 #2)")
    void dashboardToday() throws Exception {
        mvc.perform(get("/challenges/current").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today.count").value(1))
                .andExpect(jsonPath("$.today.totalPoints").value(8))
                .andExpect(jsonPath("$.today.items[0].name").value("제육볶음"))
                .andExpect(jsonPath("$.today.items[0].points").value(8))
                .andExpect(jsonPath("$.today.items[0].haggled").value(true))
                .andExpect(jsonPath("$.today.items[0].kind").value("MEAL"));
    }
}
