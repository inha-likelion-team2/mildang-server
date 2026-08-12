package app.mildang.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogicalDateTest {

    private static Instant kst(String isoLocal) {
        return ZonedDateTime.parse(isoLocal + "+09:00[Asia/Seoul]").toInstant();
    }

    @Test
    @DisplayName("05:00 이전은 전날로 귀속된다 — 명세 §0.8 예시")
    void beforeBoundaryBelongsToPreviousDay() {
        assertThat(LogicalDate.of(kst("2026-08-11T03:00:00"))).isEqualTo(LocalDate.parse("2026-08-10"));
        assertThat(LogicalDate.of(kst("2026-08-11T04:59:59"))).isEqualTo(LocalDate.parse("2026-08-10"));
    }

    @Test
    @DisplayName("05:00 정각부터 당일이다")
    void boundaryStartsNewDay() {
        assertThat(LogicalDate.of(kst("2026-08-11T05:00:00"))).isEqualTo(LocalDate.parse("2026-08-11"));
        assertThat(LogicalDate.of(kst("2026-08-11T23:50:00"))).isEqualTo(LocalDate.parse("2026-08-11"));
    }

    @Test
    @DisplayName("자정 야식 시나리오 — 밤 11:50 입력과 새벽 12:10 취식이 같은 날")
    void lateNightSnackStaysOnSameLogicalDay() {
        assertThat(LogicalDate.of(kst("2026-08-10T23:50:00")))
                .isEqualTo(LogicalDate.of(kst("2026-08-11T00:10:00")));
    }

    @Test
    @DisplayName("만료 시각은 논리적 날짜 다음날 05:00 KST — 명세 §6.8")
    void expiryIsNextDayFiveAmKst() {
        Instant expiry = LogicalDate.expiryOf(LocalDate.parse("2026-08-10"));
        assertThat(expiry).isEqualTo(kst("2026-08-11T05:00:00"));
        assertThat(LogicalDate.of(expiry)).isEqualTo(LocalDate.parse("2026-08-11"));
    }
}
