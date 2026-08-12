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
    private final boolean demoEnabled;

    public ChallengeService(ChallengeRepository challengeRepository, UserRepository userRepository,
                            PaymentRepository paymentRepository, ItemRepository itemRepository,
                            CheckinRepository checkinRepository, MildangProps props) {
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
        this.itemRepository = itemRepository;
        this.checkinRepository = checkinRepository;
        this.demoEnabled = props.demo().enabled();
    }

    @Transactional
    public CreateResponse create(String userId, CreateRequest request) {
        challengeRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, IN_PROGRESS)
                .ifPresent(c -> {
                    throw new ApiException(ErrorCode.CHALLENGE_IN_PROGRESS);
                });

        if (request.period().isFree()) {
            // demo에서는 FREE_TRIAL_USED를 발생시키지 않는다 — 심사위원 무제한 재시작 (명세 §14.4)
            User user = userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
            if (!demoEnabled) {
                if (user.isFreeTrialUsed()) {
                    throw new ApiException(ErrorCode.FREE_TRIAL_USED);
                }
                user.setFreeTrialUsed(true);
            }
        } else {
            requirePaid(userId, request);
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

        return new CreateResponse(challenge.getId(), challenge.getPeriod(), challenge.getStatus(),
                challenge.getTotalDays(), needsSurvey, null);
    }

    private void requirePaid(String userId, CreateRequest request) {
        if (request.paymentId() == null) {
            throw new ApiException(ErrorCode.PAYMENT_REQUIRED);
        }
        Payment payment = paymentRepository.findById(request.paymentId())
                .filter(p -> p.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_REQUIRED));
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new ApiException(ErrorCode.PAYMENT_REQUIRED);
        }
        if (payment.getPeriod() != request.period()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "결제한 기간과 선택한 기간이 달라요.", "paymentId", null);
        }
    }

    @Transactional(readOnly = true)
    public EstimateResponse estimate(String userId, String challengeId, Survey survey) {
        Challenge challenge = owned(userId, challengeId);
        if (challenge.getStatus() != ChallengeStatus.ONBOARDING) {
            throw new ApiException(ErrorCode.BUDGET_ALREADY_SET);
        }
        BudgetPolicy.Result result =
                BudgetPolicy.estimate(survey.noodle(), survey.bread(), survey.snack(), challenge.getPeriod());
        return new EstimateResponse(result.estimatedWeekly(), result.recommended(), result.cutRatePercent(),
                result.rationale(), ANCHORS, result.options(), result.weeklyBreakdown());
    }

    @Transactional
    public ConfirmResponse confirm(String userId, String challengeId, ConfirmRequest request) {
        Challenge challenge = owned(userId, challengeId);
        if (challenge.getStatus() != ChallengeStatus.ONBOARDING) {
            throw new ApiException(ErrorCode.BUDGET_ALREADY_SET);
        }
        Survey survey = resolveSurvey(userId, challenge, request.survey());
        validateBudget(challenge, survey, request);

        Instant now = Instant.now();
        challenge.setSurveyNoodle(survey.noodle());
        challenge.setSurveyBread(survey.bread());
        challenge.setSurveySnack(survey.snack());
        challenge.setOptionKey(request.optionKey());
        challenge.setBudget(request.budget());
        challenge.setBalance(request.budget());
        challenge.setStatus(ChallengeStatus.ACTIVE);
        challenge.setStartedAt(now);
        challenge.setEndsAt(now.plus(Duration.ofDays(challenge.getTotalDays())).minusSeconds(1));

        String tip = StartTips.of(survey.noodle(), survey.bread(), survey.snack());
        return new ConfirmResponse(challenge.getId(), challenge.getStatus(), challenge.getBudget(),
                challenge.getBalance(), challenge.getStartedAt(), challenge.getEndsAt(), new StartTip(tip));
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
        return new Survey(previous.getSurveyNoodle(), previous.getSurveyBread(), previous.getSurveySnack());
    }

    private void validateBudget(Challenge challenge, Survey survey, ConfirmRequest request) {
        if (request.budget() < 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "예산은 1 이상이어야 해요.", "budget", null);
        }
        if (request.optionKey() == OptionKey.CUSTOM) {
            return;
        }
        BudgetPolicy.Result result =
                BudgetPolicy.estimate(survey.noodle(), survey.bread(), survey.snack(), challenge.getPeriod());
        int expected = result.options().stream()
                .filter(o -> o.key() == request.optionKey())
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED, "옵션이 올바르지 않아요.", "optionKey", null))
                .budget();
        if (expected != request.budget()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "예산이 제안값과 달라요.", "budget", null);
        }
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
        String state = diff >= 5 ? "AHEAD" : diff <= -5 ? "BEHIND" : "ON_TRACK";
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
                                + i.effectivePoints() + "로 합의한 " + i.getOriginalName() + ", 드셨어요?"))
                .toList();

        boolean doneToday = checkinRepository
                .findByChallengeIdAndDate(challenge.getId(), LogicalDate.of(now)).isPresent();
        Instant dueAt = LogicalDate.of(now).atTime(22, 0).atZone(LogicalDate.KST).toInstant();

        // TODO AI 연동 후 write_dashboard_tip() 일 1회 생성·저장으로 교체 (BACKEND_NOTES §8)
        TipView tip = new TipView("tip_generic",
                "기록이 쌓이면 요일별 패턴을 짚어드릴게요. 오늘은 잔액만 확인하고 가볍게 가요.", "GENERIC");

        return new CurrentResponse(
                new ChallengeView(challenge.getId(), challenge.getPeriod(), challenge.getStatus(),
                        dayIndex, challenge.getTotalDays(), label),
                budgetView(challenge),
                new PaceView(expected, diff, note, state),
                null,
                tip,
                prepaid,
                new CheckinView(doneToday, dueAt),
                expiredConfirm);
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
        return new BudgetView(total, challenge.getBalance(), challenge.getSpentTotal(),
                challenge.getPrepaidTotal(), gauge);
    }

    /** 진행 중(ACTIVE) 챌린지 — 없으면 404. 기간이 지났으면 COMPLETED 전이 후 404. */
    @Transactional
    public Challenge requireActive(String userId) {
        Challenge challenge = challengeRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, List.of(ChallengeStatus.ACTIVE))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "진행 중인 챌린지가 없어요."));
        if (challenge.getEndsAt() != null && Instant.now().isAfter(challenge.getEndsAt())) {
            challenge.setStatus(ChallengeStatus.COMPLETED);
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
