package app.mildang.payment;

import app.mildang.challenge.Period;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public class PaymentDtos {

    public record CheckoutRequest(@NotNull Period period, @NotNull String provider, String receipt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CheckoutResponse(Boolean mocked, String id, Period period, int amountKrw,
                                   PaymentStatus status, Instant paidAt) {
    }
}
