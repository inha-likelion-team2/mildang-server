package app.mildang.challenge;

import java.util.List;

/**
 * 3문항 → 주간 추정 → 옵션 3장 → 기간 총액. AI 미사용, 전부 결정적 산식.
 *
 * v1.3 예산 모델 (§0.10): 예산은 기간 총액 하나. 기간 차이는 곱수(W1 ×1 · W2 ×2 · W4 ×4)뿐이고
 * 옵션·컷률·주간 페이스는 전 기간 동일. W4 주차 감축 커브·CUSTOM은 폐지.
 *
 * 주간 추정 산식(명세에 공식이 없어 백엔드 정의, §3.3 예시값과 일치):
 *   면(25/50/80) + 빵(15/30/50) + 간식(10/20/35) — 예: 2-3/0-1/4+ → 100 → 75/85/95
 */
public final class BudgetPolicy {

    public record Option(OptionKey key, String label, int budget, int totalBudget, String note) {
    }

    public record Result(int estimatedWeekly, int recommended, int cutRatePercent, String rationale,
                         List<Option> options, int totalBudget) {

        public Option option(OptionKey key) {
            return options.stream().filter(o -> o.key() == key).findFirst().orElseThrow();
        }
    }

    private static final int[] NOODLE = {25, 50, 80};
    private static final int[] BREAD = {15, 30, 50};
    private static final int[] SNACK = {10, 20, 35};

    private static final int[] CUT_RATES = {25, 15, 5}; // HARD · AS_IS · EASY

    private BudgetPolicy() {
    }

    public static int cutRateOf(OptionKey key) {
        return CUT_RATES[key.ordinal()];
    }

    public static Result estimate(SurveyLevel noodle, SurveyLevel bread, SurveyLevel snack, Period period) {
        int weekly = NOODLE[noodle.ordinal()] + BREAD[bread.ordinal()] + SNACK[snack.ordinal()];
        int multiplier = period.weeks();

        List<Option> options = List.of(
                option(OptionKey.HARD, "더 빡세게", weekly, multiplier, true),
                option(OptionKey.AS_IS, null, weekly, multiplier, false),
                option(OptionKey.EASY, "여유있게", weekly, multiplier, false));

        int recommended = options.get(1).budget();
        int totalBudget = options.get(1).totalBudget();

        String rationale = "평소 주 " + weekly + " 정도로 추정, 여기서 15%만 줄인 값이에요."
                + (multiplier > 1 ? " " + period.label() + "면 총 " + totalBudget + "입니다." : "");

        return new Result(weekly, recommended, cutRateOf(OptionKey.AS_IS), rationale, options, totalBudget);
    }

    private static Option option(OptionKey key, String label, int weekly, int multiplier, boolean withNote) {
        int weeklyBudget = weeklyBudget(weekly, key);
        int total = weeklyBudget * multiplier;
        String resolvedLabel = label != null ? label : "이대로 " + weeklyBudget;
        String note = withNote
                ? "빡세게 가고 싶으면 " + (multiplier > 1 ? "총 " : "") + total + "까지 내려드릴 수 있어요."
                : null;
        return new Option(key, resolvedLabel, weeklyBudget, total, note);
    }

    /** 주간 예산 = round5(주간 추정 × (1 − 컷률)) — 전 기간 공통 */
    public static int weeklyBudget(int estimatedWeekly, OptionKey key) {
        return round5(estimatedWeekly * (100 - cutRateOf(key)) / 100.0);
    }

    private static int round5(double value) {
        return (int) (Math.round(value / 5.0) * 5);
    }
}
