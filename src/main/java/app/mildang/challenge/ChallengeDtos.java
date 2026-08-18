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
     * amount는 2번(한 번 먹을 때 양), situation은 3번(가장 많이 먹는 상황),
     * weightKg는 4번(체중). 1번을 뺀 나머지는 선택 — 안 보내면 amount=NORMAL로 본다.
     *
     * <p>2번 문항의 이름은 <b>amount</b>다(FE 계약·화면 문구에 맞춘 이름). 예전 이름
     * {@code portion}도 계속 받는다 — 데모 프론트와 이미 붙인 클라이언트가 쓰고 있다.
     *
     * <p>⚠ 이름이 어긋나면 값이 «조용히» 버려진다. FE가 {@code amount}를 보내는데 서버가
     * {@code portion}만 읽던 동안, 많이 먹는 사람도 전부 NORMAL(×1.0)로 계산돼 예산이
     * 똑같이 나왔다. 400도 안 나므로 알아채기 어렵다.
     *
     * <p>{@code weightKg}는 시작 체중으로 1일차에 남는다. confirm은 최상위 {@code weightKg}도
     * 계속 받는다 — 둘 다 오면 survey 쪽을 쓴다.
     */
    public record Survey(@NotNull SurveyLevel noodle, @NotNull SurveyLevel bread, @NotNull SurveyLevel snack,
                         @com.fasterxml.jackson.annotation.JsonAlias("portion") Portion amount,
                         Situation situation,
                         @jakarta.validation.constraints.DecimalMin("20.0")
                         @jakarta.validation.constraints.DecimalMax("300.0")
                         java.math.BigDecimal weightKg) {

        public Survey {
            amount = amount != null ? amount : Portion.NORMAL;
        }

        /** 체중 없이 쓰던 자리(설문 재사용·테스트)를 그대로 두기 위한 생성자 */
        public Survey(SurveyLevel noodle, SurveyLevel bread, SurveyLevel snack,
                      Portion amount, Situation situation) {
            this(noodle, bread, snack, amount, situation, null);
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
    /** weightKg는 선택 — 화면 「체중은 어떻게 되나요?」. 시작 체중으로 1일차에 남는다 */
    public record ConfirmRequest(@Valid Survey survey, OptionKey optionKey, @NotNull Integer budget,
                                 @jakarta.validation.constraints.DecimalMin("20.0")
                                 @jakarta.validation.constraints.DecimalMax("300.0")
                                 java.math.BigDecimal weightKg) {
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
                                  ProgressView progress, TodayNoticeView todayNotice,
                                  HaggleQuotaView haggleQuota) {
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

    /** mealsLeft = 화면 「잔액 52 ・앞으로 4끼」의 «앞으로 N끼» */
    public record BudgetView(int total, int balance, int spent, int prepaid, int gaugePercent,
                             int mealsLeft) {
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
    /** 결제 화면의 「AI 밀당 대화 40회」 — unlimited면 limit·remaining이 null이다 */
    public record HaggleQuotaView(Integer limit, int used, Integer remaining, boolean unlimited) {
    }

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
    public record ProgressDay(int dayIndex, String date, boolean checkin, boolean recorded,
                              boolean weighed, boolean future) {
    }

    public record ExpiredConfirmView(String id, String logicalDate, String menuLabel, int points, String question) {
    }
}
