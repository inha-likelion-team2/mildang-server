package app.mildang.challenge;

import java.util.List;

/**
 * 3문항 → 주간 소비 추정 → 예산 옵션. AI 미사용 — 전부 결정적 산식 (2026-08-11 결정).
 *
 * 산식(명세에 공식이 없어 백엔드가 정의, 예시값과 일치하도록 보정):
 *   주간 추정 = 면(25/50/80) + 빵(15/30/50) + 간식(10/20/35), 5 단위 반올림
 *   HARD −25% · AS_IS −15% · EASY −5%
 *   예: 면 2-3, 빵 0-1, 간식 4+ → 50+15+35 = 100 → 75/85/95 (명세 §3.3 예시와 동일)
 */
public final class BudgetPolicy {

    public record Option(OptionKey key, String label, int budget, String note) {
    }

    public record WeekBudget(int week, int budget, int cutRatePercent) {
    }

    public record Result(int estimatedWeekly, int recommended, int cutRatePercent, String rationale,
                         List<Option> options, List<WeekBudget> weeklyBreakdown) {
    }

    private static final int[] NOODLE = {25, 50, 80};
    private static final int[] BREAD = {15, 30, 50};
    private static final int[] SNACK = {10, 20, 35};

    private BudgetPolicy() {
    }

    public static Result estimate(SurveyLevel noodle, SurveyLevel bread, SurveyLevel snack, Period period) {
        int weekly = NOODLE[noodle.ordinal()] + BREAD[bread.ordinal()] + SNACK[snack.ordinal()];

        int hard = scale(weekly, 25, period);
        int asIs = scale(weekly, 15, period);
        int easy = scale(weekly, 5, period);

        List<WeekBudget> breakdown = null;
        if (period == Period.W4) {
            breakdown = List.of(
                    new WeekBudget(1, round5(weekly * 0.90), 10),
                    new WeekBudget(2, round5(weekly * 0.80), 20),
                    new WeekBudget(3, round5(weekly * 0.70), 30),
                    new WeekBudget(4, round5(weekly * 0.60), 40));
            asIs = breakdown.stream().mapToInt(WeekBudget::budget).sum();
            hard = round5(asIs * 0.88);
            easy = round5(asIs * 1.12);
        }

        String rationale = "평소 주 " + weekly + " 정도로 추정, 여기서 15%만 줄인 값이에요.";
        List<Option> options = List.of(
                new Option(OptionKey.HARD, "더 빡세게", hard, "빡세게 가고 싶으면 " + hard + "까지 내려드릴 수 있어요."),
                new Option(OptionKey.AS_IS, "이대로 " + asIs, asIs, null),
                new Option(OptionKey.EASY, "여유있게", easy, null));
        return new Result(weekly, asIs, 15, rationale, options, breakdown);
    }

    /** W1은 주간값 그대로, W2는 ×2 — W2·W4 총예산 환산은 명세 미정의라 백엔드 결정 (BACKEND_NOTES §4.2) */
    private static int scale(int weekly, int cutPercent, Period period) {
        int perWeek = round5(weekly * (100 - cutPercent) / 100.0);
        return period == Period.W4 ? perWeek : perWeek * period.weeks();
    }

    private static int round5(double value) {
        return (int) (Math.round(value / 5.0) * 5);
    }
}
