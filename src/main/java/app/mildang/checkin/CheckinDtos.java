package app.mildang.checkin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CheckinDtos {

    public record TodayResponse(String date, int dayIndex, boolean done, Answers answers,
                                List<Question> questions, CheckinDays checkinDays) {
    }

    public record Answers(@NotNull ConditionValue BLOAT, @NotNull ConditionValue SKIN,
                          @NotNull ConditionValue DROWSY) {
    }

    public record Question(String key, String label, String desc) {
    }

    /** 명세 개정(§8.1) — responseRate → checkinDays. 비율·임계 없음, 원시 수치만 (표기는 FE) */
    public record CheckinDays(int answered, int elapsed, int total) {
    }

    public record PutRequest(@NotNull @Valid Answers answers) {
    }

    public record PutResponse(String id, String date, boolean done, Answers answers,
                              String message, CheckinDays checkinDays) {
    }
}
