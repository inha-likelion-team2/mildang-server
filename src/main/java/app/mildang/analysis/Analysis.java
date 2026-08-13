package app.mildang.analysis;

import app.mildang.common.model.Confidence;
import app.mildang.item.ItemKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 텍스트 분석 결과 — DB 스키마 v3.0 §7.1. resolved=false면 candidates만 채워진다 */
@Entity
@Table(name = "analyses")
@Getter
@Setter
@NoArgsConstructor
public class Analysis {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    private String challengeId;

    @Column(nullable = false)
    private String query;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemKind kind;

    @Column(nullable = false)
    private boolean resolved;

    private String name;

    private String unit;

    private Integer points;

    private Integer pm;

    @Enumerated(EnumType.STRING)
    private Confidence confidence;

    private String basis;

    /** resolved=false일 때 후보 3개 (JSON 직렬화 문자열) */
    @Column(length = 1000)
    private String candidatesJson;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;
}
