package app.mildang.payment;

import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import app.mildang.common.util.Hashes;
import app.mildang.payment.PaymentDtos.CheckoutRequest;
import app.mildang.payment.PaymentDtos.CheckoutResponse;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    /** 웹앱이라 인앱결제(IAP)가 아니라 PG다 — 토스페이먼츠 (2026-08-18 정정) */
    private static final Set<String> PROD_PROVIDERS = Set.of("TOSS");

    private final PaymentRepository paymentRepository;
    private final TossPaymentClient tossClient;
    private final boolean demoEnabled;
    private final String clientKey;

    public PaymentService(PaymentRepository paymentRepository, TossPaymentClient tossClient,
                          MildangProps props) {
        this.paymentRepository = paymentRepository;
        this.tossClient = tossClient;
        this.demoEnabled = props.demo().enabled();
        this.clientKey = props.toss().clientKey();
    }

    /**
     * demo 사양(명세 §14.4): 검증 스킵, 항상 PAID, 결제 스피너용 지연 800ms.
     * 실연동 교체 지점 #2 — ReceiptVerifier 도입 후 스토어 영수증 검증으로 교체.
     */
    @Transactional
    public CheckoutResponse checkout(String userId, CheckoutRequest request) {
        if (request.period().isFree()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "1주 챌린지는 결제가 필요 없어요.", "period", null);
        }
        boolean mockProvider = "MOCK".equals(request.provider());
        if (!mockProvider && !PROD_PROVIDERS.contains(request.provider())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 결제 수단이에요.", "provider", null);
        }
        if (!demoEnabled) {
            if (mockProvider) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 결제 수단이에요.", "provider", null);
            }
            // 실결제는 결제창을 거쳐 POST /payments/confirm 으로 들어온다
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "결제창을 거쳐 주세요.", "provider", null);
        }

        // v1.3 §4.1: 같은 receipt 재요청(더블탭)은 200 멱등 — 기존 결제 반환.
        // demo의 빈 receipt는 서로 다른 결제가 같은 해시를 갖므로 멱등 대상에서 제외 (BACKEND_NOTES §4.3)
        if (request.receipt() != null && !request.receipt().isBlank()) {
            var existing = paymentRepository.findFirstByUserIdAndProviderAndReceiptHash(
                    userId, request.provider(), Hashes.sha256(request.receipt()));
            if (existing.isPresent()) {
                Payment p = existing.get();
                return new CheckoutResponse(demoEnabled ? Boolean.TRUE : null, p.getId(), p.getPeriod(),
                        p.getAmountKrw(), p.getStatus(), p.getPaidAt());
            }
        }

        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Payment payment = new Payment();
        payment.setId(Ids.next(Ids.Prefix.PAYMENT));
        payment.setUserId(userId);
        payment.setPeriod(request.period());
        payment.setProvider(request.provider());
        payment.setReceiptHash(Hashes.sha256(request.receipt() == null ? "" : request.receipt()));
        payment.setAmountKrw(request.period().priceKrw());
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);

        return new CheckoutResponse(true, payment.getId(), payment.getPeriod(),
                payment.getAmountKrw(), payment.getStatus(), payment.getPaidAt());
    }

    /**
     * 결제창을 통과한 뒤의 승인. <b>여기서 실제로 돈이 움직인다.</b>
     *
     * <p>금액은 클라이언트 말을 믿지 않는다 — 요금제로 서버가 다시 계산해 대조한다. 안 그러면
     * 브라우저에서 amount를 100으로 바꿔 4주권을 살 수 있다.
     */
    @Transactional
    public CheckoutResponse confirm(String userId, PaymentDtos.ConfirmRequest request) {
        if (request.period().isFree()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "1주 챌린지는 결제가 필요 없어요.", "period", null);
        }
        int expected = request.period().priceKrw();
        if (request.amount() == null || request.amount() != expected) {
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        // 같은 주문번호 재요청(뒤로가기·더블탭)은 200 멱등 — 승인을 두 번 부르지 않는다
        byte[] orderHash = Hashes.sha256(request.orderId());
        var existing = paymentRepository.findFirstByUserIdAndProviderAndReceiptHash(userId, "TOSS", orderHash);
        if (existing.isPresent()) {
            Payment p = existing.get();
            return new CheckoutResponse(demoEnabled ? Boolean.TRUE : null, p.getId(), p.getPeriod(),
                    p.getAmountKrw(), p.getStatus(), p.getPaidAt());
        }

        TossPaymentClient.Approved approved =
                tossClient.confirm(request.paymentKey(), request.orderId(), expected);
        if (!"DONE".equals(approved.status()) || approved.totalAmount() != expected) {
            // 승인은 됐는데 값이 다르면 우리가 아는 결제가 아니다 — 통과시키면 안 된다
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        Payment payment = new Payment();
        payment.setId(Ids.next(Ids.Prefix.PAYMENT));
        payment.setUserId(userId);
        payment.setPeriod(request.period());
        payment.setProvider("TOSS");
        payment.setReceiptHash(orderHash);
        payment.setAmountKrw(expected);
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        payment.setCreatedAt(Instant.now());
        paymentRepository.save(payment);

        return new CheckoutResponse(null, payment.getId(), payment.getPeriod(),
                payment.getAmountKrw(), payment.getStatus(), payment.getPaidAt());
    }

    /** 결제창을 띄우는 데 필요한 클라이언트 키 */
    public PaymentDtos.PaymentConfigResponse config() {
        return new PaymentDtos.PaymentConfigResponse(
                tossClient.configured() ? clientKey : null, tossClient.configured());
    }
}
