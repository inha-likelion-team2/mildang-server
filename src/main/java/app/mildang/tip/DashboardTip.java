package app.mildang.tip;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 대시보드 한 줄 — 하루 1건, 배치 생성·조회는 읽기만 (DB 스키마 v3.0 §10.2) */
@Entity
@Table(name = "dashboard_tips", uniqueConstraints = @UniqueConstraint(columnNames = {"challengeId", "date"}))
@Getter
@Setter
@NoArgsConstructor
public class DashboardTip {

    @Id
    private String id;

    @Column(nullable = false)
    private String challengeId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(nullable = false)
    private String basis;

    @Column(nullable = false)
    private Instant createdAt;
}
