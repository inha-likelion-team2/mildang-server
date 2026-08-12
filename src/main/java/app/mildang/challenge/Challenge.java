package app.mildang.challenge;

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
public class Challenge {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Period period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChallengeStatus status;

    @Column(nullable = false)
    private int totalDays;

    /** 예산 확정 전 null */
    private Integer budget;

    /** 현재 잔액 — record·prepay에서만 변경 (음수 허용). 항등식: balance = budget − spent − prepaid (§0.10) */
    @Column(nullable = false)
    private int balance;

    /** RECORDED 항목의 effective 합. PREPAID→RECORDED 시 prepaid에서 이동 */
    @Column(nullable = false)
    private int spent;

    /** PREPAID 항목의 effective 합. spent와 겹치지 않음 */
    @Column(nullable = false)
    private int prepaid;

    /** 설문 기반 주간 추정 — W4 주차 예산 파생의 기준값 (스키마 v2.1 §6.1) */
    private Integer estimatedWeekly;

    private Integer cutRatePercent;

    private String startTipText;

    @Enumerated(EnumType.STRING)
    private SurveyLevel surveyNoodle;

    @Enumerated(EnumType.STRING)
    private SurveyLevel surveyBread;

    @Enumerated(EnumType.STRING)
    private SurveyLevel surveySnack;

    @Enumerated(EnumType.STRING)
    private OptionKey optionKey;

    @Column(nullable = false)
    private boolean needsSurvey;

    private String paymentId;

    /** 재도전 원본 챌린지 — 원본당 1회 (403 RETRY_ALREADY_USED) */
    private String retriedFromId;

    private Instant startedAt;

    private Instant endsAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
