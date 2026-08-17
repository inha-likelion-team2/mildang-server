package app.mildang.scan;

import app.mildang.common.model.Confidence;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 스캔된 메뉴 — mnu는 스캔 범위 키라 복합 PK (DB 스키마 v3.0 §7.3) */
@Entity
@IdClass(ScanMenuId.class)
@Getter
@Setter
@NoArgsConstructor
public class ScanMenu {

    @Id
    private String scanId;

    /** 1~40, 추출 순서. API의 menuId = "mnu_" + menuNo */
    @Id
    private int menuNo;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private int pm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Confidence confidence;

    private String basis;

    @Column(nullable = false)
    private boolean edited;

    /** AI 원값 보존 — 실측 수집(§5.6)의 흔적 */
    @Column(nullable = false)
    private int aiPoints;

    /** points 오름차순 — 백엔드가 정렬 */
    @Column(nullable = false)
    private int sortOrder;

    /**
     * 이 메뉴를 «탭했을 때» 상단 메모에 뜨는 밀당이 코멘트 (화면 4b).
     * 한 번 만들면 재사용한다 — 탭할 때마다 AI를 부르면 지연도 비용도 매번 든다.
     */
    @Column(length = 200)
    private String comment;
}
