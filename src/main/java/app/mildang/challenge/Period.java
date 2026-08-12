package app.mildang.challenge;

public enum Period {
    W1(7, 1, "1주", "맛보기 한 판", "최초 1회 무료 · 처음이라면 추천", 0),
    W2(14, 2, "2주", "제대로 한 판", "리포트가 뚜렷해지는 최소 기간", 2000),
    W4(28, 4, "4주", "장기전", "주차별로 예산을 나눠 드려요", 3500);

    private final int totalDays;
    private final int weeks;
    private final String label;
    private final String title;
    private final String subtitle;
    private final int priceKrw;

    Period(int totalDays, int weeks, String label, String title, String subtitle, int priceKrw) {
        this.totalDays = totalDays;
        this.weeks = weeks;
        this.label = label;
        this.title = title;
        this.subtitle = subtitle;
        this.priceKrw = priceKrw;
    }

    public int totalDays() {
        return totalDays;
    }

    public int weeks() {
        return weeks;
    }

    public String label() {
        return label;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public int priceKrw() {
        return priceKrw;
    }

    public boolean isFree() {
        return priceKrw == 0;
    }
}
