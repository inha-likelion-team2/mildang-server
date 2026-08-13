package app.mildang.scan;

import app.mildang.common.model.Confidence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 메뉴판 스캔 — DB 스키마 v3.0 §7.2. 이미지 원본은 DB에 넣지 않는다 (demo: 저장 생략) */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Scan {

    @Id
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String challengeId;

    @Column(nullable = false)
    private byte[] imageHash;

    private String place;

    @Enumerated(EnumType.STRING)
    private Confidence placeConfidence;

    private String cuisine;

    @Column(nullable = false)
    private int unreadableCount;

    /** 백엔드가 선정 (§15.3.4). 응답은 'mnu_' + menuNo */
    private Integer recommendedMenuNo;

    @Column(length = 200)
    private String recommendationComment;

    /** 추천 재현용 스냅샷 */
    @Column(nullable = false)
    private int balanceAtScan;

    @Column(nullable = false)
    private int mealsLeftAtScan;

    @Column(nullable = false)
    private Instant scannedAt;
}
