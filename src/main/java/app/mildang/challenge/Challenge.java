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

    /** 요청으로 받은 주간값 = options[optionKey].budget — 검증 재현용 (스키마 v3.0 §6.1) */
    private Integer budgetWeekly;

    /** 저장·응답되는 기간 총액 = budgetWeekly × 곱수(1·2·4). 확정 전 null */
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

    /** 한 번 먹을 때 양 — 예산 추정에 곱한다 (0.7 / 1.0 / 1.3) */
    @Enumerated(EnumType.STRING)
    private Portion surveyPortion;

    /** 가장 많이 먹는 상황 — 예산에는 반영하지 않고, 나중에 AI가 쓰도록 저장만 한다 */
    @Enumerated(EnumType.STRING)
    private Situation surveySituation;

    @Enumerated(EnumType.STRING)
    private OptionKey optionKey;

    @Column(nullable = false)
    private boolean needsSurvey;

    private String paymentId;

    private Instant startedAt;

    private Instant endsAt;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
