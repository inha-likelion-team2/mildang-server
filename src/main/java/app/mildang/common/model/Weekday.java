package app.mildang.common.model;

import java.time.DayOfWeek;

public enum Weekday {
    MON(DayOfWeek.MONDAY, "월요일"),
    TUE(DayOfWeek.TUESDAY, "화요일"),
    WED(DayOfWeek.WEDNESDAY, "수요일"),
    THU(DayOfWeek.THURSDAY, "목요일"),
    FRI(DayOfWeek.FRIDAY, "금요일"),
    SAT(DayOfWeek.SATURDAY, "토요일"),
    SUN(DayOfWeek.SUNDAY, "일요일");

    private final DayOfWeek dayOfWeek;
    private final String korean;

    Weekday(DayOfWeek dayOfWeek, String korean) {
        this.dayOfWeek = dayOfWeek;
        this.korean = korean;
    }

    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    public String korean() {
        return korean;
    }

    public static Weekday of(DayOfWeek dayOfWeek) {
        for (Weekday value : values()) {
            if (value.dayOfWeek == dayOfWeek) {
                return value;
            }
        }
        throw new IllegalArgumentException("알 수 없는 요일: " + dayOfWeek);
    }
}
