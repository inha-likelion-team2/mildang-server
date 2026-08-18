package app.mildang.payment;

import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 토스페이먼츠 결제 승인 (§14.7 #2 실연동 자리).
 *
 * <p>브라우저에서 결제창을 통과하면 <code>paymentKey</code>가 나온다. 그것만으로는 아직 결제가
 * 아니고, <b>서버가 승인 API를 불러야</b> 실제로 돈이 움직인다. 이 단계를 브라우저에 맡기면
 * 시크릿 키가 노출되고, 금액을 클라이언트가 정하게 된다.
 *
 * <p>승인은 <b>되돌리기 어렵다</b>(취소는 별도 API). 그래서 부르기 전에 금액을 서버가 다시 계산해
 * 대조하고, 같은 주문번호로 두 번 부르지 않도록 호출부에서 멱등 처리한다.
 */
@Component
public class TossPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(TossPaymentClient.class);

    private final MildangProps.Toss config;
    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    public TossPaymentClient(MildangProps props) {
        this.config = props.toss();
        this.http = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
    }

    public boolean configured() {
        return config.secretKey() != null && !config.secretKey().isBlank();
    }

    /** 승인 결과 — 화면·기록에 필요한 것만 */
    public record Approved(String status, String orderId, int totalAmount, String method,
                           String approvedAt) {
    }

    /**
     * 결제를 승인한다.
     *
     * @param amount 서버가 계산한 금액. 클라이언트가 보낸 값을 그대로 넘기면 안 된다
     */
    public Approved confirm(String paymentKey, String orderId, int amount) {
        if (!configured()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "결제가 아직 설정되지 않았어요.", "provider", null);
        }
        String body = """
                {"paymentKey":"%s","orderId":"%s","amount":%d}"""
                .formatted(paymentKey, orderId, amount);
        // Basic 인증: 시크릿 키 뒤에 콜론을 붙여 base64 (비밀번호 없는 형태)
        String basic = Base64.getEncoder()
                .encodeToString((config.secretKey() + ":").getBytes(StandardCharsets.UTF_8));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.confirmUri()))
                    .timeout(config.timeout())
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/json")
                    // 같은 주문번호로 두 번 눌러도 토스 쪽에서 한 번만 처리되게
                    .header("Idempotency-Key", orderId)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = om.readTree(response.body());

            if (response.statusCode() != 200) {
                // 토스가 주는 사유는 로그에만 — 사용자에겐 다시 시도하라고만 한다
                log.warn("토스 결제 승인 실패 {} — {} / {}", response.statusCode(),
                        json.path("code").asString(""), json.path("message").asString(""));
                throw new ApiException(ErrorCode.PAYMENT_FAILED);
            }
            return new Approved(
                    json.path("status").asString(""),
                    json.path("orderId").asString(""),
                    json.path("totalAmount").asInt(0),
                    json.path("method").asString(""),
                    json.path("approvedAt").asString(""));
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.PAYMENT_FAILED);
        } catch (Exception e) {
            // 여기서 터지면 «승인이 됐는지 안 됐는지 모르는» 상태다 — 로그를 남겨 대사할 수 있게
            log.error("토스 결제 승인 중 오류 (orderId={}) — 승인 여부 확인 필요", orderId, e);
            throw new ApiException(ErrorCode.PAYMENT_FAILED);
        }
    }
}
