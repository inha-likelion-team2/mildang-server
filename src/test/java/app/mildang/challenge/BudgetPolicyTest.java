package app.mildang.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetPolicyTest {

    @Test
    @DisplayName("명세 §3.3 예시 — 면 2-3·빵 0-1·간식 4+ → 주 100 추정, 주간 75/85/95")
    void matchesSpecExample() {
        BudgetPolicy.Result result =
                BudgetPolicy.estimate(SurveyLevel.MID, SurveyLevel.LOW, SurveyLevel.HIGH, Period.W1);

        assertThat(result.estimatedWeekly()).isEqualTo(100);
        assertThat(result.recommended()).isEqualTo(85);
        assertThat(result.options()).extracting(BudgetPolicy.Option::budget).containsExactly(75, 85, 95);
        assertThat(result.options()).extracting(BudgetPolicy.Option::totalBudget).containsExactly(75, 85, 95);
        assertThat(result.totalBudget()).isEqualTo(85);
        assertThat(result.rationale()).contains("주 100", "15%");
    }

    @Test
    @DisplayName("v1.3 §0.10 — 총액은 주간 × 곱수뿐: W2 150/170/190, W4 300/340/380")
    void totalIsWeeklyTimesMultiplier() {
        BudgetPolicy.Result w2 =
                BudgetPolicy.estimate(SurveyLevel.MID, SurveyLevel.LOW, SurveyLevel.HIGH, Period.W2);
        assertThat(w2.options()).extracting(BudgetPolicy.Option::budget).containsExactly(75, 85, 95);
        assertThat(w2.options()).extracting(BudgetPolicy.Option::totalBudget).containsExactly(150, 170, 190);
        assertThat(w2.totalBudget()).isEqualTo(170);

        BudgetPolicy.Result w4 =
                BudgetPolicy.estimate(SurveyLevel.MID, SurveyLevel.LOW, SurveyLevel.HIGH, Period.W4);
        assertThat(w4.options()).extracting(BudgetPolicy.Option::totalBudget).containsExactly(300, 340, 380);
        assertThat(w4.totalBudget()).isEqualTo(340);
        assertThat(w4.rationale()).contains("총 340");
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
