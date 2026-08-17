package app.mildang.challenge;

/**
 * 설문 2번 «한 번 먹을 때 양은 어느 정도인가요?» (화면 온보딩_03).
 * 주간 추정치에 곱한다 — 같은 횟수라도 한 번에 먹는 양이 다르면 예산이 달라야 한다.
 */
public enum Portion {
    SMALL(0.7),   // 조금 먹어요
    NORMAL(1.0),  // 보통이에요
    LARGE(1.3);   // 많이 먹어요

    private final double multiplier;

    Portion(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return multiplier;
    }
}
