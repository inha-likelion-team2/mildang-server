package app.mildang.item;

import app.mildang.common.model.Confidence;
import app.mildang.common.model.Weekday;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 3a(약속)·3b(식사) 공용 항목 — 명세 §6.1.
 * original_* 은 생성 후 불변, 흥정 결과는 adjusted_* 에만 기록한다.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Item {

    @Id
    private String id;

    @Column(nullable = false)
    private String challengeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    private String sourceRefId;

    // ---- original (불변) ----
    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String originalUnit;

    @Column(nullable = false)
    private int originalPoints;

    @Column(nullable = false)
    private int originalPm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Confidence originalConfidence;

    @Column(nullable = false)
    private String originalBasis;

    // ---- adjusted (흥정 결과, 없으면 null) ----
    private String adjustedLabel;

    private Integer adjustedPoints;

    private String adjustedBasis;

    private String adjustedHaggleId;

    private Integer adjustedTurns;

    // ---- 기타 ----
    @Enumerated(EnumType.STRING)
    private Weekday weekday;

    @Column(nullable = false)
    private LocalDate logicalDate;

    private Integer targetWeek;

    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant recordedAt;

    public int effectivePoints() {
        return adjustedPoints != null ? adjustedPoints : originalPoints;
    }

    public boolean isHaggled() {
        return adjustedPoints != null;
    }
}
