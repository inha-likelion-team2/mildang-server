package app.mildang.weight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 하루 한 건의 체중 기록 (화면 3 대시보드의 «1일차 58kg · 2일차 55kg»).
 *
 * 값만 남긴다 — 예산·잔액·리포트 통계 어디에도 관여하지 않는다.
 * 나중에 리포트의 AI 분석이 «밀가루 많이 먹은 주의 체중 움직임» 같은 걸 볼 때 쓴다
 * (팀 결정 2026-08-17). 그때까지는 저장·조회만.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"challengeId", "date"}))
@Getter
@Setter
@NoArgsConstructor
public class WeightLog {

    @Id
    private String id;

    @Column(nullable = false)
    private String challengeId;

    @Column(nullable = false)
    private String userId;

    /** 논리적 날짜 (05:00 KST 경계) — 체크인과 같은 기준 */
    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int dayIndex;

    /** kg, 소수점 한 자리 (58.5) */
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal weightKg;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
