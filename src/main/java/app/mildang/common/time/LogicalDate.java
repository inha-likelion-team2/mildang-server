package app.mildang.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 일자 경계 유틸 — 명세 §0.8. 모든 "하루" 판정은 반드시 이 클래스를 거친다.
 * 경계는 매일 05:00 KST (자정 아님). 결제·토큰 만료 등 시스템 시각에는 적용하지 않는다.
 */
public final class LogicalDate {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final LocalTime BOUNDARY = LocalTime.of(5, 0);

    private LogicalDate() {
    }

    /** 예: 2026-08-11 03:00 KST → 2026-08-10 */
    public static LocalDate of(Instant instant) {
        ZonedDateTime kst = instant.atZone(KST);
        LocalDate date = kst.toLocalDate();
        return kst.toLocalTime().isBefore(BOUNDARY) ? date.minusDays(1) : date;
    }

    /** 항목 만료 시각 = 논리적 날짜 다음날 05:00 KST (명세 §6.8) */
    public static Instant expiryOf(LocalDate logicalDate) {
        return logicalDate.plusDays(1).atTime(BOUNDARY).atZone(KST).toInstant();
    }
}
