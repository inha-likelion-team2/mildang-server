package app.mildang.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetPolicyTest {

    @Test
    @DisplayName("참조 스케일 정합 가중치 — 면 2-3·빵 0-1·간식 4+ → 주 265 추정, 주간 200/225/250 (팀 결정 2026-08-14, §3.3 예시 대체)")
    void matchesReferenceScale() {
        BudgetPolicy.Result result =
                BudgetPolicy.estimate(SurveyLevel.MID, SurveyLevel.LOW, SurveyLevel.HIGH, Period.W1);

        assertThat(result.estimatedWeekly()).isEqualTo(265);
        assertThat(result.recommended()).isEqualTo(225);
        assertThat(result.options()).extracting(BudgetPolicy.Option::budget).containsExactly(200, 225, 250);
        assertThat(result.options()).extracting(BudgetPolicy.Option::totalBudget).containsExactly(200, 225, 250);
        assertThat(result.totalBudget()).isEqualTo(225);
        assertThat(result.rationale()).contains("주 265", "15%");
    }

    @Test
    @DisplayName("v1.3 §0.10 — 총액은 주간 × 곱수뿐: W2 400/450/500, W4 800/900/1000")
    void totalIsWeeklyTimesMultiplier() {
        BudgetPolicy.Result w2 =
                BudgetPolicy.estimate(SurveyLevel.MID, SurveyLevel.LOW, SurveyLevel.HIGH, Period.W2);
        assertThat(w2.options()).extracting(BudgetPolicy.Option::budget).containsExactly(200, 225, 250);
        assertThat(w2.options()).extracting(BudgetPolicy.Option::totalBudget).containsExactly(400, 450, 500);
        assertThat(w2.totalBudget()).isEqualTo(450);

        BudgetPolicy.Result w4 =
                BudgetPolicy.estimate(SurveyLevel.MID, SurveyLevel.LOW, SurveyLevel.HIGH, Period.W4);
        assertThat(w4.options()).extracting(BudgetPolicy.Option::totalBudget).containsExactly(800, 900, 1000);
        assertThat(w4.totalBudget()).isEqualTo(900);
        assertThat(w4.rationale()).contains("총 900");
    }

    @Test
    @DisplayName("27조합 전부에 시작 팁이 존재한다")
    void startTipCoversAllCombinations() {
        for (SurveyLevel n : SurveyLevel.values()) {
            for (SurveyLevel b : SurveyLevel.values()) {
                for (SurveyLevel s : SurveyLevel.values()) {
                    String tip = StartTips.of(n, b, s);
                    assertThat(tip).isNotBlank();
                    assertThat(tip.length()).isLessThanOrEqualTo(90);
                }
            }
        }
    }
}
