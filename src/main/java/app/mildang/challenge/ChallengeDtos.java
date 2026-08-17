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
    /**
     * 화면 «온보딩_03» 설문. noodle·bread·snack은 1번 문항(주당 빈도),
     * portion은 2번(한 번 먹을 때 양), situation은 3번(가장 많이 먹는 상황).
     * 뒤 둘은 선택 — 안 보내면 portion=NORMAL로 보고, situation은 비워둔다.
     */
    public record Survey(@NotNull SurveyLevel noodle, @NotNull SurveyLevel bread, @NotNull SurveyLevel snack,
                         Portion portion, Situation situation) {

        public Survey {
            portion = portion != null ? portion : Portion.NORMAL;
        }
    }

    public record EstimateRequest(@NotNull @Valid Survey survey) {
    }

    /** slider는 화면 «온보딩_03»(가볍게 ↔ 넉넉하게)이 쓴다. options는 하위 호환으로 남겨둔 값 */
    public record EstimateResponse(int estimatedWeekly, int recommended, int cutRatePercent,
                                   String rationale, List<Anchor> anchors,
                                   List<BudgetPolicy.Option> options, int totalBudget,
                                   BudgetPolicy.Slider slider) {
    }

    public record Anchor(String label, int points) {
    }

    // ---- POST /challenges/{id}/budget ----
    /**
     * budget은 주간값 — 저장·응답은 기간 총액.
     * 슬라이더로 바뀌면서 «제안값 3개 중 하나»가 아니라 범위 안의 아무 값이나 받는다.
     * optionKey는 선택 — 안 보내면 서버가 값에서 가장 가까운 걸 기록한다.
     */
    public record ConfirmRequest(@Valid Survey survey, OptionKey optionKey, @NotNull Integer budget) {
    }

    /** 예산 조정 — "나중에도 언제든지 조정할 수 있어요" */
    public record AdjustBudgetRequest(@NotNull Integer budget) {
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
                                  CheckinView checkin, List<ExpiredConfirmView> expiredConfirm,
                                  List<app.mildang.weight.WeightDtos.WeightPoint> weights,
                                  ProgressView progress, TodayNoticeView todayNotice) {
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

    /**
     * 화면 「오늘의 알림」 카드 — <b>오늘 잡혀 있는 약속</b>을 보여준다 (2026-08-17 확인).
     * 밀당이 말풍선의 AI 팁({@link TipView})과는 다른 자리다.
     */
    public record TodayNoticeView(String date, String text, List<TodayPromiseView> promises) {
    }

    /** prepaid=true면 이미 예산에서 미리 빼둔 약속 */
    public record TodayPromiseView(String id, String name, int points, String weekday, boolean prepaid) {
    }

    /**
     * 화면 「1주 챌린지 진행률」의 체크박스 N개 — 시작일부터 총 일수만큼, 지난 날은 채운 여부가 들어간다.
     * 체크인 기준인지 기록 기준인지는 화면이 정하도록 <b>둘 다</b> 준다.
     */
    public record ProgressView(int dayIndex, int totalDays, List<ProgressDay> days) {
    }

    /** future=true면 아직 오지 않은 날 — 체크박스를 비워두면 된다 */
    public record ProgressDay(int dayIndex, String date, boolean checkin, boolean recorded, boolean future) {
    }

    public record ExpiredConfirmView(String id, String logicalDate, String menuLabel, int points, String question) {
    }
}
