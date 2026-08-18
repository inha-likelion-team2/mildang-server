package app.mildang.payment;

import app.mildang.challenge.Period;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class PaymentDtos {

    /**
     * 결제창을 통과한 뒤 서버에 승인을 요청한다.
     * ⚠ amount는 <b>대조용</b>이다 — 서버가 요금제로 다시 계산해 다르면 거절한다.
     */
    public record ConfirmRequest(@NotNull Period period,
                                 @jakarta.validation.constraints.NotBlank String paymentKey,
                                 @jakarta.validation.constraints.NotBlank String orderId,
                                 @NotNull Integer amount) {
    }

    /** 결제창을 띄우는 데 필요한 값 — 클라이언트 키는 노출돼도 되는 값이다 */
    public record PaymentConfigResponse(String clientKey, boolean configured) {
    }

    public record CheckoutRequest(@NotNull Period period, @NotNull String provider, String receipt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckoutResponse(Boolean mocked, String id, Period period, int amountKrw,
                                   PaymentStatus status, Instant paidAt) {
    }
}
