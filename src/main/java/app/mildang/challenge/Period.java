package app.mildang.challenge;

public enum Period {
    W1(7, 1, "1주", "맛보기", "최초 1회 무료 · 처음이라면 추천", 0, 20),
    W2(14, 2, "2주", "단기", "리포트가 뚜렷해지는 최소 기간", 2000, 40),
    W4(28, 4, "4주", "장기", "주차별로 예산을 나눠 드려요", 3500, 0);

    private final int totalDays;
    private final int weeks;
    private final String label;
    private final String title;
    private final String subtitle;
    private final int priceKrw;
    /** 밀당 대화 횟수 — 대화 1번(세션 하나)이 1회. 0이면 무제한 (결제 화면 표기와 같은 값) */
    private final int haggleQuota;

    /** 0은 «무제한» — 4주 요금제 */
    public static final int UNLIMITED = 0;

    Period(int totalDays, int weeks, String label, String title, String subtitle,
           int priceKrw, int haggleQuota) {
        this.totalDays = totalDays;
        this.weeks = weeks;
        this.label = label;
        this.title = title;
        this.subtitle = subtitle;
        this.priceKrw = priceKrw;
        this.haggleQuota = haggleQuota;
    }

    public int haggleQuota() {
        return haggleQuota;
    }

    public boolean isHaggleUnlimited() {
        return haggleQuota == UNLIMITED;
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
