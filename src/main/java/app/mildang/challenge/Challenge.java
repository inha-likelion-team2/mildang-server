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

    /** 선차감 반영된 현재 잔액 — record·prepay에서만 변경 (음수 허용) */
    @Column(nullable = false)
    private int balance;

    @Column(nullable = false)
    private int spentTotal;

    @Column(nullable = false)
    private int prepaidTotal;

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

    private Instant startedAt;

    private Instant endsAt;

    @Column(nullable = false)
    private Instant createdAt;
}
