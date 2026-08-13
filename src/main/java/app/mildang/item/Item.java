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
 * 3a(약속)·3b(식사) 공용 항목 + 잔액 차감 기록 — DB 스키마 v2.1 §8.
 * original_* 은 생성 후 불변. balance_* 스냅샷은 차감 시점에 1회만 기록(COALESCE 보존, §8.2).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Item {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

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

    /** TEXT→anl_* · PRESET→pst_* · IMAGE→null (스캔은 아래 두 컬럼, 스키마 v3.0 §8.1) */
    private String sourceRefId;

    /** IMAGE만 — mnu는 스캔 범위 키라 (scanId, menuNo) 쌍으로 참조 */
    private String sourceScanId;

    private Integer sourceMenuNo;

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

    // ---- 잔액 차감 스냅샷 (record·prepay에서 1회만 기록. null = 아직 미차감) ----
    private Integer balanceBefore;

    private Integer balanceAfter;

    private Integer balanceIfOriginal;

    // ---- 기타 ----
    @Enumerated(EnumType.STRING)
    private Weekday weekday;

    @Column(nullable = false)
    private LocalDate logicalDate;

    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant recordedAt;

    private Instant prepaidAt;

    private Instant expiredAt;

    private Instant canceledAt;

    public int effectivePoints() {
        return adjustedPoints != null ? adjustedPoints : originalPoints;
    }

    public boolean isHaggled() {
        return adjustedPoints != null;
    }

    public boolean isDeducted() {
        return balanceAfter != null;
    }

    /** 차감 스냅샷 — 이미 값이 있으면 보존한다 (스키마 §8.2 COALESCE) */
    public void snapshotBalances(int before, int after, int ifOriginal) {
        if (this.balanceBefore == null) {
            this.balanceBefore = before;
        }
        if (this.balanceAfter == null) {
            this.balanceAfter = after;
        }
        if (this.balanceIfOriginal == null) {
            this.balanceIfOriginal = ifOriginal;
        }
    }
}
