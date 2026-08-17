package app.mildang.challenge;

import app.mildang.challenge.ChallengeDtos.Anchor;
import app.mildang.challenge.ChallengeDtos.BudgetView;
import app.mildang.challenge.ChallengeDtos.ChallengeView;
import app.mildang.challenge.ChallengeDtos.CheckinView;
import app.mildang.challenge.ChallengeDtos.ConfirmRequest;
import app.mildang.challenge.ChallengeDtos.ConfirmResponse;
import app.mildang.challenge.ChallengeDtos.CreateRequest;
import app.mildang.challenge.ChallengeDtos.CreateResponse;
import app.mildang.challenge.ChallengeDtos.CurrentResponse;
import app.mildang.challenge.ChallengeDtos.EstimateResponse;
import app.mildang.challenge.ChallengeDtos.ExpiredConfirmView;
import app.mildang.challenge.ChallengeDtos.PaceView;
import app.mildang.challenge.ChallengeDtos.PrepaidItemView;
import app.mildang.challenge.ChallengeDtos.StartTip;
import app.mildang.challenge.ChallengeDtos.Survey;
import app.mildang.challenge.ChallengeDtos.TipView;
import app.mildang.challenge.ChallengeDtos.TodayItemView;
import app.mildang.challenge.ChallengeDtos.TodayView;
import app.mildang.checkin.CheckinRepository;
import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import app.mildang.common.time.LogicalDate;
import app.mildang.item.Item;
import app.mildang.item.ItemRepository;
import app.mildang.item.ItemStatus;
import app.mildang.payment.Payment;
import app.mildang.payment.PaymentRepository;
import app.mildang.payment.PaymentStatus;
import app.mildang.user.User;
import app.mildang.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeService {

    private static final List<Anchor> ANCHORS = List.of(
            new Anchor("라면 한 번", 80), new Anchor("김밥 한 줄", 20), new Anchor("삼겹살", 0));
    private static final List<ChallengeStatus> IN_PROGRESS =
            List.of(ChallengeStatus.ONBOARDING, ChallengeStatus.ACTIVE);

    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final ItemRepository itemRepository;
    private final CheckinRepository checkinRepository;
    private final app.mildang.tip.TipService tipService;
    private final app.mildang.weight.WeightService weightService;
    private final boolean demoEnabled;

    public ChallengeService(ChallengeRepository challengeRepository, UserRepository userRepository,
                            PaymentRepository paymentRepository, ItemRepository itemRepository,
                            CheckinRepository checkinRepository, app.mildang.tip.TipService tipService,
                            @org.springframework.context.annotation.Lazy app.mildang.weight.WeightService weightService,
                            MildangProps props) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.itemRepository = itemRepository;
        this.checkinRepository = checkinRepository;
        this.tipService = tipService;
        this.weightService = weightService;
        this.demoEnabled = props.demo().enabled();
    }

    @Transactional
    public CreateResponse create(String userId, CreateRequest request) {
        // 기간이 끝난 ACTIVE를 여기서 확정한다. requireActive의 전이는 예외와 함께 롤백되므로
        // 이 경로가 없으면 "current는 404인데 새 챌린지는 409"인 상태에 갇힌다.
        completeStaleActive(userId);

        Challenge inProgress = challengeRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, IN_PROGRESS).orElse(null);
        if (inProgress != null) {
            // 예산 확정 전에 이탈했다가 다시 들어온 경우 — 409로 막으면 빠져나갈 API가 없어 계정이 잠긴다.
            // 아직 예산도 항목도 없는 껍데기이므로 그대로 이어서 쓴다.
            if (inProgress.getStatus() == ChallengeStatus.ONBOARDING) {
                return resumeOnboarding(inProgress, userId, request);
            }
            throw new ApiException(ErrorCode.CHALLENGE_IN_PROGRESS);
        }

        Payment payment = null;
        if (request.period().isFree()) {
            // demo에서는 FREE_TRIAL_USED를 발생시키지 않는다 — 심사위원 무제한 재시작 (명세 §14.4)
            // free_trial_used=true 마킹은 생성이 아니라 예산 확정(ACTIVE) 시점 (스키마 v2.1 §4.1)
            User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
            if (!demoEnabled && user.isFreeTrialUsed()) {
                throw new ApiException(ErrorCode.FREE_TRIAL_USED);
            }
        } else {
            payment = requirePaid(userId, request);
        }

        boolean needsSurvey = challengeRepository
                .findFirstByUserIdAndSurveyNoodleNotNullOrderByCreatedAtDesc(userId).isEmpty();

        Challenge challenge = new Challenge();
        challenge.setId(Ids.next(Ids.Prefix.CHALLENGE));
        challenge.setUserId(userId);
        challenge.setPeriod(request.period());
        challenge.setStatus(ChallengeStatus.ONBOARDING);
        challenge.setTotalDays(request.period().totalDays());
        challenge.setNeedsSurvey(needsSurvey);
        challenge.setPaymentId(request.paymentId());
        challenge.setCreatedAt(Instant.now());
        challengeRepository.save(challenge);

        if (payment != null) {
            payment.setConsumedByChallengeId(challenge.getId()); // 결제 1건 = 챌린지 1건 (스키마 v2.1 §5.1)
        }

        return new CreateResponse(challenge.getId(), challenge.getPeriod(), challenge.getStatus(),
                challenge.getTotalDays(), needsSurvey, null);
    }

    /**
     * 예산 확정 전 챌린지를 이어서 쓴다.
     * 기간이 달라졌으면 갈아끼우되, 이미 결제가 붙어 있으면 그 결제를 버리지 않으려고 기존 기간을 유지한다.
     */
    private CreateResponse resumeOnboarding(Challenge existing, String userId, CreateRequest request) {
        if (existing.getPeriod() != request.period() && existing.getPaymentId() == null) {
            Payment payment = request.period().isFree() ? null : requirePaid(userId, request);
            existing.setPeriod(request.period());
            existing.setTotalDays(request.period().totalDays());
            existing.setPaymentId(request.paymentId());
            if (payment != null) {
                payment.setConsumedByChallengeId(existing.getId());
            }
        }
        return new CreateResponse(existing.getId(), existing.getPeriod(), existing.getStatus(),
                existing.getTotalDays(), existing.isNeedsSurvey(), null);
    }

    /** 기간이 끝났는데 ACTIVE로 남아 있는 챌린지를 COMPLETED로 확정 (쓰기 트랜잭션에서 커밋된다) */
    private void completeStaleActive(String userId) {
        challengeRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of(ChallengeStatus.ACTIVE))
                .filter(c -> c.getEndsAt() != null && Instant.now().isAfter(c.getEndsAt()))
                .ifPresent(c -> {
                    c.setStatus(ChallengeStatus.COMPLETED);
                    c.setCompletedAt(Instant.now());
                });
    }

    private Payment requirePaid(String userId, CreateRequest request) {
        if (request.paymentId() == null) {
            throw new ApiException(ErrorCode.PAYMENT_REQUIRED);
        }
        Payment payment = paymentRepository.findById(request.paymentId())
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_REQUIRED));
        if (payment.getConsumedByChallengeId() != null) {
            throw new ApiException(ErrorCode.PAYMENT_ALREADY_USED); // v1.3 §4.1
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new ApiException(ErrorCode.PAYMENT_REQUIRED);
        }
        if (payment.getPeriod() != request.period()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "결제한 기간과 선택한 기간이 달라요.", "paymentId", null);
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public EstimateResponse estimate(String userId, String challengeId, Survey survey) {
        Challenge challenge = owned(userId, challengeId);
        if (challenge.getStatus() != ChallengeStatus.ONBOARDING) {
            throw new ApiException(ErrorCode.BUDGET_ALREADY_SET);
        }
        BudgetPolicy.Result result = BudgetPolicy.estimate(
                survey.noodle(), survey.bread(), survey.snack(), survey.portion(), challenge.getPeriod());
        return new EstimateResponse(result.estimatedWeekly(), result.recommended(), result.cutRatePercent(),
                result.rationale(), ANCHORS, result.options(), result.totalBudget(), result.slider());
    }

    @Transactional
    public ConfirmResponse confirm(String userId, String challengeId, ConfirmRequest request) {
        Challenge challenge = owned(userId, challengeId);
        if (challenge.getStatus() != ChallengeStatus.ONBOARDING) {
            throw new ApiException(ErrorCode.BUDGET_ALREADY_SET);
        }
        Survey survey = resolveSurvey(userId, challenge, request.survey());
        BudgetPolicy.Result estimate = BudgetPolicy.estimate(
                survey.noodle(), survey.bread(), survey.snack(), survey.portion(), challenge.getPeriod());

        // 요청 budget은 주간값. 디자인(온보딩_03)이 슬라이더로 바뀌면서 «제안값 3개 중 하나»가 아니라
        // 범위 안의 아무 값이나 받는다. optionKey는 보내면 기록하고, 없으면 값에서 가장 가까운 걸 추정한다.
        if (!BudgetPolicy.inRange(request.budget(), estimate.slider())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "예산은 " + estimate.slider().min() + "~" + estimate.slider().max()
                            + " 사이에서 " + estimate.slider().step() + " 단위로 정해주세요.", "budget", null);
        }
        OptionKey optionKey = request.optionKey() != null
                ? request.optionKey() : nearestOption(estimate, request.budget());
        int totalBudget = request.budget() * challenge.getPeriod().weeks(); // 저장·응답은 기간 총액

        Instant now = Instant.now();
        challenge.setSurveyNoodle(survey.noodle());
        challenge.setSurveyBread(survey.bread());
        challenge.setSurveySnack(survey.snack());
        challenge.setSurveyPortion(survey.portion());
        challenge.setSurveySituation(survey.situation());
        challenge.setOptionKey(optionKey);
        challenge.setBudgetWeekly(request.budget());
        challenge.setBudget(totalBudget);
        challenge.setBalance(totalBudget);
        challenge.setEstimatedWeekly(estimate.estimatedWeekly());
        challenge.setCutRatePercent(BudgetPolicy.cutRateOf(optionKey));
        challenge.setStatus(ChallengeStatus.ACTIVE);
        challenge.setStartedAt(now);
        challenge.setEndsAt(now.plus(Duration.ofDays(challenge.getTotalDays())).minusSeconds(1));

        String tip = StartTips.of(survey.noodle(), survey.bread(), survey.snack());
        challenge.setStartTipText(tip);

        // W1 무료 체험 소진은 ACTIVE 시점에 마킹 — 온보딩 이탈자가 무료를 잃지 않도록 (스키마 v2.1 §4.1)
        if (challenge.getPeriod().isFree() && !demoEnabled) {
            userRepository.findById(userId).ifPresent(u -> u.setFreeTrialUsed(true));
        }

        return new ConfirmResponse(challenge.getId(), challenge.getStatus(), challenge.getPeriod(),
                challenge.getBudget(), challenge.getBalance(),
                challenge.getStartedAt(), challenge.getEndsAt(), new StartTip(tip));
    }

    /** 슬라이더 값에 가장 가까운 옵션 — 리포트의 «다음엔 한 단계 세게» 추천이 이 값을 쓴다 */
    private static OptionKey nearestOption(BudgetPolicy.Result estimate, int weeklyBudget) {
        return estimate.options().stream()
                .min(java.util.Comparator.comparingInt(o -> Math.abs(o.budget() - weeklyBudget)))
                .map(BudgetPolicy.Option::key)
                .orElse(OptionKey.AS_IS);
    }

    /**
     * 예산 조정 — 화면 «온보딩_03»의 "나중에도 언제든지 조정할 수 있어요".
     * 이미 쓴 것(spent·prepaid)은 건드리지 않고 총액만 다시 잡는다.
     * 새 총액이 이미 쓴 것보다 적으면 잔액이 음수가 되는데, 그건 초과로 정상 처리한다(불변 조건 5).
     */
    @Transactional
    public ConfirmResponse adjustBudget(String userId, String challengeId, int weeklyBudget) {
        Challenge challenge = owned(userId, challengeId);
        if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "진행 중인 챌린지만 예산을 조정할 수 있어요.", "budget", null);
        }
        BudgetPolicy.Result estimate = BudgetPolicy.estimate(
                challenge.getSurveyNoodle(), challenge.getSurveyBread(), challenge.getSurveySnack(),
                challenge.getSurveyPortion(), challenge.getPeriod());
        if (!BudgetPolicy.inRange(weeklyBudget, estimate.slider())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "예산은 " + estimate.slider().min() + "~" + estimate.slider().max()
                            + " 사이에서 " + estimate.slider().step() + " 단위로 정해주세요.", "budget", null);
        }

        int totalBudget = weeklyBudget * challenge.getPeriod().weeks();
        challenge.setBudgetWeekly(weeklyBudget);
        challenge.setBudget(totalBudget);
        // 항등식 유지: balance = total − spent − prepaid
        challenge.setBalance(totalBudget - challenge.getSpent() - challenge.getPrepaid());
        challenge.setOptionKey(nearestOption(estimate, weeklyBudget));
        challenge.setCutRatePercent(BudgetPolicy.cutRateOf(challenge.getOptionKey()));

        return new ConfirmResponse(challenge.getId(), challenge.getStatus(), challenge.getPeriod(),
                challenge.getBudget(), challenge.getBalance(),
                challenge.getStartedAt(), challenge.getEndsAt(), null);
    }

    private Survey resolveSurvey(String userId, Challenge challenge, Survey requested) {
        if (requested != null) {
            return requested;
        }
        if (challenge.isNeedsSurvey()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "첫 챌린지에는 설문이 필요해요.", "survey", null);
        }
        Challenge previous = challengeRepository
                .findFirstByUserIdAndSurveyNoodleNotNullOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "설문이 필요해요.", "survey", null));
        // 재사용할 때도 양·상황을 함께 가져온다 — 안 그러면 재대결에서 예산이 달라진다
        return new Survey(previous.getSurveyNoodle(), previous.getSurveyBread(), previous.getSurveySnack(),
                previous.getSurveyPortion(), previous.getSurveySituation());
    }

    @Transactional
    public CurrentResponse current(String userId) {
        Challenge challenge = requireActive(userId);
        Instant now = Instant.now();

        int dayIndex = dayIndex(challenge, now);
        String label = challenge.getPeriod().label() + " 챌린지 · " + dayIndex + "일차";

        int daysLeft = challenge.getTotalDays() - dayIndex;
        int expected = (int) Math.round(challenge.getBudget() * daysLeft / (double) challenge.getTotalDays());
        int diff = challenge.getBalance() - expected;
        // v1.3 §3.5: 부호만으로 판정 (임계 없음)
        String state = diff > 0 ? "AHEAD" : diff < 0 ? "BEHIND" : "ON_TRACK";
        String note = switch (state) {
            case "AHEAD" -> "페이스보다 +" + diff + " 앞서 있어요";
            case "BEHIND" -> Math.abs(diff) + " 뒤에 있어요 — 따라잡을 수 있어요";
            default -> "딱 페이스대로 가고 있어요";
        };

        List<PrepaidItemView> prepaid = itemRepository
                .findByChallengeIdAndStatusInOrderByCreatedAtDesc(challenge.getId(), List.of(ItemStatus.PREPAID))
                .stream()
                .map(i -> new PrepaidItemView(i.getId(),
                        i.getWeekday().korean() + " " + i.getOriginalName() + " 약속",
                        i.effectivePoints(),
                        i.getWeekday().name(),
                        "사전 결재 · 예산에서 미리 빼뒀어요"))
                .toList();

        List<ExpiredConfirmView> expiredConfirm = itemRepository
                .findByChallengeIdAndStatusInOrderByCreatedAtDesc(challenge.getId(), List.of(ItemStatus.EXPIRED))
                .stream()
                .limit(3)
                .map(i -> new ExpiredConfirmView(i.getId(), i.getLogicalDate().toString(), menuLabel(i),
                        i.effectivePoints(),
                        i.getLogicalDate().format(DateTimeFormatter.ofPattern("M월 d일")) + "에 "
                                + i.effectivePoints() + "으로 합의한 " + i.getOriginalName() + ", 드셨어요?"))
                .toList();

        // 오늘 먹은 것 — 05:00 경계 기준 오늘 기록된 항목 (선차감분은 요일 경과 배치가 RECORDED로 넘긴다)
        LocalDate today = LogicalDate.of(now);
        List<TodayItemView> todayItems = itemRepository
                .findByChallengeIdAndLogicalDateAndStatusInOrderByRecordedAtAsc(
                        challenge.getId(), today, List.of(ItemStatus.RECORDED))
                .stream()
                .map(i -> new TodayItemView(i.getId(), i.getOriginalName(),
                        i.isHaggled() ? i.getAdjustedLabel() : i.getOriginalUnit(),
                        i.effectivePoints(), i.isHaggled(), i.getKind(), i.getRecordedAt()))
                .toList();
        TodayView todayView = new TodayView(today.toString(), todayItems.size(),
                todayItems.stream().mapToInt(TodayItemView::points).sum(), todayItems);

        boolean doneToday = checkinRepository
                .findByChallengeIdAndDate(challenge.getId(), LogicalDate.of(now)).isPresent();
        Instant dueAt = LogicalDate.of(now).atTime(22, 0).atZone(LogicalDate.KST).toInstant();

        // 일 1회 생성·저장 — 없으면 즉석 1회 시도, 실패 시 null(FE 영역 숨김) (§15.5)
        app.mildang.tip.DashboardTip storedTip = tipService.todayTip(challenge, dayIndex, diff);
        TipView tip = storedTip == null ? null
                : new TipView(storedTip.getId(), storedTip.getText(), storedTip.getBasis());

        return new CurrentResponse(
                new ChallengeView(challenge.getId(), challenge.getPeriod(), challenge.getStatus(),
                        dayIndex, challenge.getTotalDays(), label),
                budgetView(challenge),
                new PaceView(expected, diff, note, state),
                null,
                tip,
                todayView,
                prepaid,
                new CheckinView(doneToday, dueAt),
                expiredConfirm,
                weightService.series(challenge.getId()));
    }

    private static String menuLabel(Item item) {
        return item.getAdjustedLabel() != null
                ? item.getOriginalName() + " " + item.getAdjustedLabel()
                : item.getOriginalName();
    }

    public int dayIndex(Challenge challenge, Instant now) {
        LocalDate start = LogicalDate.of(challenge.getStartedAt());
        int index = (int) ChronoUnit.DAYS.between(start, LogicalDate.of(now)) + 1;
        return Math.max(1, Math.min(challenge.getTotalDays(), index));
    }

    public BudgetView budgetView(Challenge challenge) {
        int total = challenge.getBudget() == null ? 0 : challenge.getBudget();
        int gauge = total == 0 ? 0
                : Math.max(0, Math.min(100, (int) Math.round(challenge.getBalance() * 100.0 / total)));
        return new BudgetView(total, challenge.getBalance(), challenge.getSpent(),
                challenge.getPrepaid(), gauge);
    }

    /** 진행 중(ACTIVE) 챌린지 — 없으면 404. 기간이 지났으면 COMPLETED 전이 후 404. */
    @Transactional
    public Challenge requireActive(String userId) {
        Challenge challenge = challengeRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of(ChallengeStatus.ACTIVE))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "진행 중인 챌린지가 없어요."));
        if (challenge.getEndsAt() != null && Instant.now().isAfter(challenge.getEndsAt())) {
            challenge.setStatus(ChallengeStatus.COMPLETED);
            challenge.setCompletedAt(Instant.now());
            throw new ApiException(ErrorCode.NOT_FOUND, "진행 중인 챌린지가 없어요.");
        }
        return challenge;
    }

    private Challenge owned(String userId, String challengeId) {
        return challengeRepository.findById(challengeId)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
