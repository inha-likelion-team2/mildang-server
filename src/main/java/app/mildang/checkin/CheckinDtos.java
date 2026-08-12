package app.mildang.checkin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CheckinDtos {

    public record TodayResponse(String date, int dayIndex, boolean done, Answers answers,
                                List<Question> questions, ResponseRate responseRate) {
    }

    public record Answers(@NotNull ConditionValue BLOAT, @NotNull ConditionValue SKIN,
                          @NotNull ConditionValue DROWSY) {
    }

    public record Question(String key, String label, String desc) {
    }

    public record ResponseRate(int answered, int total, int percent, int reportThreshold) {
    }

    public record PutRequest(@NotNull @Valid Answers answers) {
    }

    public record PutResponse(String id, String date, boolean done, Answers answers,
                              String message, ResponseRate responseRate) {
    }
}
