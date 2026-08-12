package app.mildang.item;

import app.mildang.common.model.Confidence;
import java.util.List;
import java.util.Optional;

/**
 * 자주 먹는 것 기본 4종 (명세 §6.7). 최근 4주 입력 빈도 기반 HISTORY 집계는 이력이 쌓이면 교체.
 * 표시 가격은 항상 original 기준 — 과거 합의값 사용 금지 (부록 A #3).
 */
public final class Presets {

    public record Preset(String id, String name, String unit, int points, int pm,
                         Confidence confidence, String basis) {
    }

    public static final List<Preset> DEFAULTS = List.of(
            new Preset("pst_ramen", "라면", "1봉지", 80, 10, Confidence.CERTAIN, "면 전체가 밀 — 봉지라면 1인분"),
            new Preset("pst_bread", "빵", "1개", 45, 10, Confidence.HIGH, "밀가루 반죽 — 기본 1개 기준"),
            new Preset("pst_tteok", "떡볶이", "1인분", 55, 10, Confidence.HIGH, "떡·어묵에 밀 혼합 — 1인분 기준"),
            new Preset("pst_chicken", "치킨", "1마리", 70, 10, Confidence.HIGH, "튀김옷이 밀가루 — 프라이드 기준"));

    private Presets() {
    }

    public static Optional<Preset> byId(String id) {
        return DEFAULTS.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
