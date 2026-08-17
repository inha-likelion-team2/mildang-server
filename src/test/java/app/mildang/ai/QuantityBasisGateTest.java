package app.mildang.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §8 #12 — 추정에 «무엇 얼마만큼» 기준이 적혀 있는지.
 *
 * <p>없으면 사용자가 80이라는 숫자를 믿을 근거가 없고, 「절반으로 줄이자」가 무엇의 절반인지도
 * 정의되지 않는다.
 */
class QuantityBasisGateTest {

    @Test
    @DisplayName("★ 근거에 기준 수량이 있으면 통과")
    void acceptsBasisWithQuantity() {
        assertTrue(AiGates.hasQuantityBasis("1봉지", "면 전체가 밀 — 봉지라면 1인분 기준"));
        assertTrue(AiGates.hasQuantityBasis("1인분", "튀김옷이 밀가루 — 프라이드 1인분 기준"));
        assertTrue(AiGates.hasQuantityBasis("1개", "밀가루 반죽 — 기본 1개 기준"));
    }

    @Test
    @DisplayName("★ 우리말 수량도 인정한다 — 「한 그릇」「반 봉지」")
    void acceptsKoreanQuantityWords() {
        assertTrue(AiGates.hasQuantityBasis("그릇", "면이 밀 — 한 그릇 기준"));
        assertTrue(AiGates.hasQuantityBasis("봉지", "반 봉지 기준으로 봤어요"));
    }

    @Test
    @DisplayName("★ 수량 없는 두루뭉술한 근거는 막는다")
    void rejectsVagueBasis() {
        assertFalse(AiGates.hasQuantityBasis("인분", "밀가루가 들어갑니다"));
        assertFalse(AiGates.hasQuantityBasis("인분", "밀 함량이 높은 편이에요"));
        assertFalse(AiGates.hasQuantityBasis("인분", ""));
        assertFalse(AiGates.hasQuantityBasis("인분", null));
    }

    @Test
    @DisplayName("★ unit이 비면 무조건 막는다 — 흥정의 기준선이 사라진다")
    void rejectsMissingUnit() {
        assertFalse(AiGates.hasQuantityBasis(null, "봉지라면 1인분 기준"));
        assertFalse(AiGates.hasQuantityBasis("", "봉지라면 1인분 기준"));
        assertFalse(AiGates.hasQuantityBasis("  ", "봉지라면 1인분 기준"));
    }

    @Test
    @DisplayName("단위 자체가 수량을 담고 있으면 근거가 두루뭉술해도 통과")
    void unitAloneCanCarryTheQuantity() {
        // 「1봉지」면 무엇 얼마만큼인지 이미 정해진다
        assertTrue(AiGates.hasQuantityBasis("1봉지", "밀가루가 들어갑니다"));
        assertTrue(AiGates.hasQuantityBasis("200g", "밀 함량이 높아요"));
    }

    @Test
    @DisplayName("기본 프리셋 4종은 전부 게이트를 통과한다 — 우리 기준값이 규칙을 지켜야 한다")
    void ourOwnPresetsPass() {
        for (app.mildang.item.Presets.Preset preset : app.mildang.item.Presets.DEFAULTS) {
            assertTrue(AiGates.hasQuantityBasis(preset.unit(), preset.basis()),
                    "프리셋 " + preset.name() + "의 근거에 기준 수량이 없다: " + preset.basis());
        }
    }
}
