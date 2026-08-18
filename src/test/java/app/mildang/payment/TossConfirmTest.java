package app.mildang.payment;

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
 * 실결제 승인 (§14.7 #2) — 토스페이먼츠.
 *
 * <p>여기서 지키려는 건 하나다: <b>금액을 클라이언트가 정하지 못한다.</b> 브라우저에서 amount를
 * 100으로 바꿔 4주권을 사는 길이 없어야 한다. 키가 없는 환경에서는 결제 경로 자체가 닫힌다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TossConfirmTest {

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

    private String confirmBody(String period, int amount) {
        return """
                {"period":"%s","paymentKey":"test_payment_key","orderId":"mildang-test-0001","amount":%d}"""
                .formatted(period, amount);
    }

    @Test
    @Order(1)
    @DisplayName("준비 — 로그인")
    void setup() throws Exception {
        MvcResult login = mvc.perform(post("/auth/social")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"KAKAO","idToken":"toss-user","deviceId":"d-toss"}
                                """))
                .andExpect(status().isOk()).andReturn();
        token = json(login).get("accessToken").asText();
    }

    @Test
    @Order(2)
    @DisplayName("★ 금액이 요금제와 다르면 승인 전에 거절한다 — 토스를 부르지도 않는다")
    void rejectsTamperedAmount() throws Exception {
        // 2주는 2,000원인데 100원으로 보낸다
        mvc.perform(post("/payments/confirm")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("W2", 100)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_AMOUNT_MISMATCH"));
    }

    @Test
    @Order(3)
    @DisplayName("1주는 무료라 결제 자체를 받지 않는다")
    void freePeriodCannotBePaid() throws Exception {
        mvc.perform(post("/payments/confirm")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("W1", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("period"));
    }

    @Test
    @Order(4)
    @DisplayName("★ 키가 없으면 결제 경로가 닫힌다 — 열어두면 «승인 없이 결제된 척»이 된다")
    void closedWithoutKeys() throws Exception {
        // 금액은 맞지만 시크릿 키가 없어 승인 단계에서 막힌다
        mvc.perform(post("/payments/confirm")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("W2", 2000)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("결제가 아직 설정되지 않았어요")));
    }

    @Test
    @Order(5)
    @DisplayName("결제 설정 여부를 화면이 물어볼 수 있다")
    void configTellsWhetherItIsReady() throws Exception {
        mvc.perform(get("/payments/config").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.clientKey").doesNotExist());
    }

    @Test
    @Order(6)
    @DisplayName("데모 결제는 그대로 동작한다 — 시연이 막히지 않게")
    void mockStillWorks() throws Exception {
        mvc.perform(post("/payments/checkout")
                        .header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"period":"W2","provider":"MOCK"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.amountKrw").value(2000));
    }
}
