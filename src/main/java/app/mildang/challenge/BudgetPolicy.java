package app.mildang.challenge;

import java.util.List;

/**
 * 3문항 → 주간 추정 → 옵션 3장 → 기간 총액. AI 미사용, 전부 결정적 산식.
 *
 * v1.3 예산 모델 (§0.10): 예산은 기간 총액 하나. 기간 차이는 곱수(W1 ×1 · W2 ×2 · W4 ×4)뿐이고
 * 옵션·컷률·주간 페이스는 전 기간 동일. W4 주차 감축 커브·CUSTOM은 폐지.
 *
 * 주간 추정 산식(명세에 공식이 없어 백엔드 정의):
 *   가중치 = 참조 스케일(§15.2.4 라면 80·빵 45·간식 ~20) × 주간 빈도 중앙값(0.5/2.5/4.5회).
 *   예: 면 2-3 · 빵 0-1 · 간식 4+ → 265 → 200/225/250.
 *   ⚠ §3.3 예시(→100→75/85/95)와 다름 — 그 값은 라면 한 번(80)에 주 예산이 끝나 챌린지가
 *   성립하지 않아 팀 결정(2026-08-14)으로 상향. §14.5 시드는 명세값 그대로(estimate와 무관).
 */
public final class BudgetPolicy {

    public record Option(OptionKey key, String label, int budget, int totalBudget, String note) {
    }

    /**
     * 예산 슬라이더 범위 — 화면 «온보딩_03»(가볍게 ↔ 넉넉하게)이 쓴다.
     * 추정치를 기준으로 잡아야 추천값이 트랙 가운데쯤 오고 좌우로 감이 잡힌다.
     * 고정 범위로 두면 추정 190인 사람의 추천값이 트랙 왼쪽 끝에 붙어버린다.
     */
    public record Slider(int min, int max, int step, int recommended) {
    }

    public record Result(int estimatedWeekly, int recommended, int cutRatePercent, String rationale,
                         List<Option> options, int totalBudget, Slider slider) {

        public Option option(OptionKey key) {
            return options.stream().filter(o -> o.key() == key).findFirst().orElseThrow();
        }
    }

    public static final int STEP = 5;
    private static final int ABSOLUTE_MIN = 30;  // 이보다 낮으면 챌린지가 성립하지 않는다
    private static final int ABSOLUTE_MAX = 900; // 주간 상한

    private static final int[] NOODLE = {30, 150, 270};
    private static final int[] BREAD = {25, 110, 200};
    private static final int[] SNACK = {10, 50, 90};

    private static final int[] CUT_RATES = {25, 15, 5}; // HARD · AS_IS · EASY

    private BudgetPolicy() {
    }

    public static int cutRateOf(OptionKey key) {
        return CUT_RATES[key.ordinal()];
    }

    public static Result estimate(SurveyLevel noodle, SurveyLevel bread, SurveyLevel snack, Period period) {
        return estimate(noodle, bread, snack, Portion.NORMAL, period);
    }

    /** 체중 보정의 기준점 — 이 몸무게에서 곱수가 정확히 1.0이다 */
    private static final double WEIGHT_BASE_KG = 60.0;

    /** 기준 대비 편차에 곱하는 계수. 0.3이면 90kg에서 +15%가 되어 한계와 맞아떨어진다 */
    private static final double WEIGHT_SLOPE = 0.3;

    private static final double WEIGHT_MIN = 0.85;
    private static final double WEIGHT_MAX = 1.15;

    public static Result estimate(SurveyLevel noodle, SurveyLevel bread, SurveyLevel snack,
                                  Portion amount, Period period) {
        return estimate(noodle, bread, snack, amount, null, period);
    }

    /**
     * 체중 보정 곱수 — 60kg에서 1.0, ±15%에서 멈춘다.
     *
     * <p>몸이 크면 같은 «1인분»도 실제 양이 크다는 것 이상은 주장하지 않는다. 그래서 기울기를
     * 얕게 두고 한계를 박았다 — 보정이 설문 자체(면·빵·간식 빈도)를 뒤집으면 안 된다.
     * 체중을 안 보내면 1.0이라 기존 결과가 그대로 나온다.
     */
    public static double weightMultiplier(java.math.BigDecimal weightKg) {
        if (weightKg == null) {
            return 1.0;
        }
        double m = 1 + (weightKg.doubleValue() - WEIGHT_BASE_KG) / WEIGHT_BASE_KG * WEIGHT_SLOPE;
        return Math.clamp(m, WEIGHT_MIN, WEIGHT_MAX);
    }

    /**
     * 한 번 먹는 양(amount)과 체중을 곱한다 — 같은 횟수라도 양이 다르면 예산이 달라야 하고,
     * 몸이 크면 같은 «1인분»도 실제 양이 크다.
     *
     * <p>먹는 «상황»(Situation)은 예산에 반영하지 않는다 (전부 1.0, 팀 결정 2026-08-17에서
     * 유지 재확인 2026-08-19). 저장은 하되 산식에는 안 들어간다 — 시작 팁 문구 쪽에서 쓴다.
     */
    public static Result estimate(SurveyLevel noodle, SurveyLevel bread, SurveyLevel snack,
                                  Portion amount, java.math.BigDecimal weightKg, Period period) {
        int base = NOODLE[noodle.ordinal()] + BREAD[bread.ordinal()] + SNACK[snack.ordinal()];
        double weightM = weightMultiplier(weightKg);
        int weekly = round5(base * (amount != null ? amount : Portion.NORMAL).multiplier() * weightM);
        int multiplier = period.weeks();

        List<Option> options = List.of(
                option(OptionKey.HARD, "더 빡세게", weekly, multiplier, true),
                option(OptionKey.AS_IS, null, weekly, multiplier, false),
                option(OptionKey.EASY, "여유있게", weekly, multiplier, false));

        int recommended = options.get(1).budget();
        int totalBudget = options.get(1).totalBudget();

        // 톤 규칙: 추정치에는 근거 한 줄이 붙는다. 체중을 받았을 때만 그 사실을 밝힌다 —
        // 안 밝히면 «왜 나만 예산이 다르지»가 되고, 늘 밝히면 안 보낸 사람에게 거짓말이 된다.
        String weightNote = weightM == 1.0 ? "" : " 체중까지 감안했어요.";
        String rationale = "평소 주 " + weekly + " 정도로 추정, 여기서 15%만 줄인 값이에요." + weightNote
                + (multiplier > 1 ? " " + period.label() + "면 총 " + totalBudget + "입니다." : "");

        return new Result(weekly, recommended, cutRateOf(OptionKey.AS_IS), rationale, options, totalBudget,
                slider(weekly, recommended));
    }

    /** 추정치 기준 0.3배 ~ 1.6배, 절대 한계로 클램프 */
    public static Slider slider(int estimatedWeekly, int recommended) {
        int min = Math.max(ABSOLUTE_MIN, round5(estimatedWeekly * 0.3));
        int max = Math.min(ABSOLUTE_MAX, round5(estimatedWeekly * 1.6));
        if (max <= min) {
            max = min + STEP * 4; // 추정이 아주 작아도 움직일 여지는 남긴다
        }
        return new Slider(min, max, STEP, Math.clamp(recommended, min, max));
    }

    /** 슬라이더가 실제로 만들 수 있는 값인지 — confirm·조정 양쪽에서 쓴다 */
    public static boolean inRange(int weeklyBudget, Slider slider) {
        return weeklyBudget >= slider.min() && weeklyBudget <= slider.max()
                && weeklyBudget % slider.step() == 0;
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
