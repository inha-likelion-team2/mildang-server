package app.mildang.checkin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CheckinDtos {

    public record TodayResponse(String date, int dayIndex, boolean done, Answers answers,
                                List<Question> questions, CheckinDays checkinDays,
                                java.math.BigDecimal weightKg,
                                /** 아직 오늘 안 쟀을 때 스테퍼가 출발할 값 (가장 최근 기록) */
                                java.math.BigDecimal lastWeightKg) {
    }

    public record Answers(@NotNull ConditionValue BLOAT, @NotNull ConditionValue SKIN,
                          @NotNull ConditionValue DROWSY) {
    }

    public record Question(String key, String label, String desc) {
    }

    /** 명세 개정(§8.1) — responseRate → checkinDays. 비율·임계 없음, 원시 수치만 (표기는 FE) */
    public record CheckinDays(int answered, int elapsed, int total) {
    }

    /** weightKg는 선택 — 화면에 «건너뛰어도 괜찮아요»가 있고, 컨디션만 남기는 날도 있다 */
    public record PutRequest(@NotNull @Valid Answers answers,
                             @jakarta.validation.constraints.DecimalMin("20.0")
                             @jakarta.validation.constraints.DecimalMax("300.0")
                             java.math.BigDecimal weightKg,
                                /** 아직 오늘 안 쟀을 때 스테퍼가 출발할 값 (가장 최근 기록) */
                                java.math.BigDecimal lastWeightKg) {
    }

    public record PutResponse(String id, String date, boolean done, Answers answers,
                              String message, CheckinDays checkinDays,
                              java.math.BigDecimal weightKg) {
    }
}
