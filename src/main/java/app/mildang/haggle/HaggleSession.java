package app.mildang.haggle;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 흥정 세션 — DB 스키마 v3.0 §9.1. frame은 개설 시 확정 후 고정 (§7.6) */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class HaggleSession {

    @Id
    private String id;

    @Column(nullable = false)
    private String itemId;

    /** 횟수는 «이번 판» 기준이라 챌린지로 센다 */
    @Column(nullable = false, length = 40)
    private String challengeId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String entryPoint;

    /** OPEN → CLOSED / ABANDONED */
    @Column(nullable = false)
    private String status;

    /** 서버가 단일 진실 (§15.8.1) */
    @Column(nullable = false)
    private int turn;

    @Column(nullable = false)
    private int maxTurns = 10;

    @Column(nullable = false)
    private String frame;

    /** 개설 시 original 복사 — 제안 상한 검증용 */
    @Column(nullable = false)
    private String targetName;

    @Column(nullable = false)
    private int targetPoints;

    private String targetPlace;

    @Column(nullable = false)
    private int balanceAtOpen;

    private String agreedKey;

    private String agreedLabel;

    private Integer agreedPoints;

    private String agreedBasis;

    @Column(nullable = false, length = 500)
    private String openingText;

    @Column(nullable = false, length = 500)
    private String chipsJson;

    private Instant closedAt;

    @Column(nullable = false)
    private Instant createdAt;
}
