package app.mildang.payment;

import app.mildang.common.auth.CurrentUser;
import app.mildang.payment.PaymentDtos.CheckoutRequest;
import app.mildang.payment.PaymentDtos.CheckoutResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** 결제창을 띄울 클라이언트 키 */
    @org.springframework.web.bind.annotation.GetMapping("/config")
    public PaymentDtos.PaymentConfigResponse config() {
        return paymentService.config();
    }

    /** 결제창 통과 후 승인 — 여기서 실제로 돈이 움직인다 */
    @PostMapping("/confirm")
    public PaymentDtos.CheckoutResponse confirm(@CurrentUser String userId,
                                                @Valid @RequestBody PaymentDtos.ConfirmRequest request) {
        return paymentService.confirm(userId, request);
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@CurrentUser String userId, @Valid @RequestBody CheckoutRequest request) {
        return paymentService.checkout(userId, request);
    }
}
