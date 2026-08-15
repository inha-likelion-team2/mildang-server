package app.mildang.challenge;

import app.mildang.item.ItemKind;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public class ChallengeDtos {

    // ---- GET /plans ----
    public record PlansResponse(List<PlanCard> plans, String notice) {
    }

    public record PlanCard(Period period, String title, String subtitle, int priceKrw,
                           boolean recommended, boolean available, String unavailableReason) {
    }

    // ---- POST /challenges ----
    public record CreateRequest(@NotNull Period period, String paymentId) {
    }

    public record CreateResponse(String id, Period period, ChallengeStatus status,
                                 int totalDays, boolean needsSurvey, Instant startedAt) {
    }

    // ---- 설문 ----
    public record Survey(@NotNull SurveyLevel noodle, @NotNull SurveyLevel bread, @NotNull SurveyLevel snack) {
    }

    public record EstimateRequest(@NotNull @Valid Survey survey) {
    }

    public record EstimateResponse(int estimatedWeekly, int recommended, int cutRatePercent,
                                   String rationale, List<Anchor> anchors,
                                   List<BudgetPolicy.Option> options, int totalBudget) {
    }

    public record Anchor(String label, int points) {
    }

    // ---- POST /challenges/{id}/budget ----
    /** budget은 주간값(options[optionKey].budget) — 저장·응답은 기간 총액 (§3.4) */
    public record ConfirmRequest(@Valid Survey survey, @NotNull OptionKey optionKey, @NotNull Integer budget) {
    }

    public record ConfirmResponse(String id, ChallengeStatus status, Period period, int budget, int balance,
                                  Instant startedAt, Instant endsAt, StartTip startTip) {
    }

    public record StartTip(String text) {
    }

    // ---- GET /challenges/current ----
    public record CurrentResponse(ChallengeView challenge, BudgetView budget, PaceView pace,
                                  WeeklyView weekly, TipView tip, TodayView today,
                                  List<PrepaidItemView> prepaidItems,
                                  CheckinView checkin, List<ExpiredConfirmView> expiredConfirm) {
    }

    /** 오늘(05:00 경계) 기록된 것 — 메인 화면 "오늘 먹은 것" 블록. 비어 있으면 items=[] */
    public record TodayView(String date, int count, int totalPoints, List<TodayItemView> items) {
    }

    /** label은 흥정했으면 합의 표현, 아니면 원래 단위. points는 effective */
    public record TodayItemView(String id, String name, String label, int points,
                                boolean haggled, ItemKind kind, Instant recordedAt) {
    }

    public record ChallengeView(String id, Period period, ChallengeStatus status,
                                int dayIndex, int totalDays, String label) {
    }

    public record BudgetView(int total, int balance, int spent, int prepaid, int gaugePercent) {
    }

    public record PaceView(int expectedBalance, int diff, String note, String state) {
    }

    public record WeeklyView(int week, int budget, int balance) {
    }

    public record TipView(String id, String text, String basis) {
    }

    public record PrepaidItemView(String id, String name, int points, String weekday, String note) {
    }

    public record CheckinView(boolean doneToday, Instant dueAt) {
    }

    public record ExpiredConfirmView(String id, String logicalDate, String menuLabel, int points, String question) {
    }
}
