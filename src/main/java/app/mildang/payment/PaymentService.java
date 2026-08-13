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

    private static final Set<String> PROD_PROVIDERS = Set.of("IAP_APPLE", "IAP_GOOGLE");

    private final PaymentRepository paymentRepository;
    private final boolean demoEnabled;

    public PaymentService(PaymentRepository paymentRepository, MildangProps props) {
        this.paymentRepository = paymentRepository;
        this.demoEnabled = props.demo().enabled();
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
            throw new UnsupportedOperationException("IAP 영수증 검증은 실연동 시 구현 (명세 §14.7 #2)");
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
}
