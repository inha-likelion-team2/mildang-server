package app.mildang.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BudgetPolicyTest {

    @Test
    @DisplayName("명세 §3.3 예시 — 면 2-3·빵 0-1·간식 4+ → 주 100 추정, 75/85/95 옵션")
    void matchesSpecExample() {
        BudgetPolicy.Result result =
                BudgetPolicy.estimate(SurveyLevel.MID, SurveyLevel.LOW, SurveyLevel.HIGH, Period.W1);

        assertThat(result.estimatedWeekly()).isEqualTo(100);
        assertThat(result.recommended()).isEqualTo(85);
        assertThat(result.options()).extracting(BudgetPolicy.Option::budget).containsExactly(75, 85, 95);
        assertThat(result.rationale()).contains("주 100", "15%");
        assertThat(result.weeklyBreakdown()).isNull();
    }

    @Test
    @DisplayName("W4는 주차별 −10/−20/−30/−40% 분해가 내려간다 (명세 §3.3)")
    void w4HasWeeklyBreakdown() {
        BudgetPolicy.Result result =
                BudgetPolicy.estimate(SurveyLevel.MID, SurveyLevel.LOW, SurveyLevel.HIGH, Period.W4);

        assertThat(result.weeklyBreakdown()).hasSize(4);
        assertThat(result.weeklyBreakdown()).extracting(BudgetPolicy.WeekBudget::cutRatePercent)
                .containsExactly(10, 20, 30, 40);
        int sum = result.weeklyBreakdown().stream().mapToInt(BudgetPolicy.WeekBudget::budget).sum();
        assertThat(result.recommended()).isEqualTo(sum);
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
