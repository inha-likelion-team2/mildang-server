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

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(@CurrentUser String userId, @Valid @RequestBody CheckoutRequest request) {
        return paymentService.checkout(userId, request);
    }
}
