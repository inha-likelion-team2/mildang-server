package app.mildang.payment;

import app.mildang.challenge.Period;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Period period;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private int amountKrw;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    /** 영수증 SHA-256 — 리플레이 차단 (스키마 v2.1 §5.1) */
    @Column(nullable = false)
    private byte[] receiptHash;

    /** 결제 1건 = 챌린지 1건. null이면 아직 미사용 */
    private String consumedByChallengeId;

    private Instant paidAt;

    @Column(nullable = false)
    private Instant createdAt;
}
