package app.mildang.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 완주 리포트 + 공유 카드 — 완주 시 1회 생성, 조회 시 재생성 없음 (DB 스키마 v3.0 §10.3) */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String challengeId;

    @Column(nullable = false)
    private String title;

    /** [{key,label,value,sub}] — 순서가 화면 계약 */
    @Column(nullable = false, length = 1000)
    private String statsJson;

    @Column(nullable = false)
    private int totalSpent;

    @Column(nullable = false)
    private boolean findingAvailable;

    private String findingHeadline;

    private String findingConditionKey;

    private Integer findingThresholdPoints;

    private Double findingRatio;

    private String findingSampleNote;

    @Column(nullable = false)
    private int answeredDays;

    @Column(nullable = false)
    private int totalDays;

    @Column(nullable = false)
    private int haggleTotalSaved;

    private String haggleBestItemId;

    @Column(nullable = false)
    private double haggleAvgTurns;

    @Column(nullable = false)
    private int haggleLongestTurns;

    @Column(nullable = false)
    private String nextPeriod;

    @Column(nullable = false)
    private String nextOptionKey;

    /** 주간값 — POST /budget의 budget에 그대로 들어감 (§9.1) */
    @Column(nullable = false)
    private int nextSuggestedBudget;

    @Column(nullable = false, unique = true)
    private String inviteCode;

    private String cardImageUrl;

    @Column(length = 500)
    private String cardMentionsJson;

    private Instant cardExpiresAt;

    @Column(nullable = false)
    private Instant generatedAt;
}
