package app.mildang.demo;

import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeRepository;
import app.mildang.challenge.ChallengeStatus;
import app.mildang.challenge.OptionKey;
import app.mildang.challenge.Period;
import app.mildang.challenge.SurveyLevel;
import app.mildang.checkin.Checkin;
import app.mildang.checkin.CheckinRepository;
import app.mildang.checkin.ConditionValue;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import app.mildang.common.model.Confidence;
import app.mildang.common.model.Weekday;
import app.mildang.common.time.LogicalDate;
import app.mildang.item.Item;
import app.mildang.item.ItemKind;
import app.mildang.item.ItemRepository;
import app.mildang.item.ItemStatus;
import app.mildang.item.SourceType;
import app.mildang.payment.PaymentRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 심사용 시드 — 명세 §14.5·§14.6. 현재 로그인 계정의 상태를 시나리오로 강제 세팅한다.
 * 수치는 §0.10 항등식(balance = total − spent − prepaid)에 맞게 정합화 — 명세 §14.5의
 * "잔액 52 · spent 33 · 선차감 70"은 항등식과 모순이라 잔액 50 구성으로 대체 (BACKEND_NOTES §9).
 */
@Service
@Profile({"local", "demo"})
public class DemoSeedService {

    private final ChallengeRepository challengeRepository;
    private final ItemRepository itemRepository;
    private final CheckinRepository checkinRepository;
    private final PaymentRepository paymentRepository;

    private int runningBalance; // RECORDED 시드 항목의 잔액 스냅샷 재현용

    public DemoSeedService(ChallengeRepository challengeRepository, ItemRepository itemRepository,
                           CheckinRepository checkinRepository, PaymentRepository paymentRepository) {
        this.challengeRepository = challengeRepository;
        this.itemRepository = itemRepository;
        this.checkinRepository = checkinRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void reset(String userId) {
        List<String> challengeIds = challengeRepository.findByUserId(userId)
                .stream().map(Challenge::getId).toList();
        if (!challengeIds.isEmpty()) {
            itemRepository.deleteAll(itemRepository.findByChallengeIdIn(challengeIds));
            checkinRepository.deleteAll(checkinRepository.findByChallengeIdIn(challengeIds));
            challengeRepository.deleteAllById(challengeIds);
        }
        paymentRepository.deleteAll(paymentRepository.findByUserId(userId));
    }

    @Transactional
    public Challenge seed(String userId, String scenario) {
        reset(userId);
        return switch (scenario) {
            case "FRESH" -> null;
            case "DAY4_ACTIVE" -> day4Active(userId);
            case "COMPLETED" -> completed(userId);
            case "W4_DAY12" -> w4Day12(userId);
            case "LOW_BALANCE" -> lowBalance(userId);
            case "EXPIRED_CONFIRM" -> expiredConfirm(userId);
            default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "지원하지 않는 시나리오예요.", "scenario", null);
        };
    }

    /** W1 4일차 — 기록 2건(잔액 50), 흥정 완료 항목 1건(3b), 약속 대기 1건(3a), 체크인 3일 */
    private Challenge day4Active(String userId) {
        Challenge c = challenge(userId, Period.W1, 85, 3, ChallengeStatus.ACTIVE);
        record(c, "김밥", "1줄", 20, Confidence.HIGH, "단무지·어묵에 소량", 2, null);
        record(c, "제육볶음", "1인분", 15, Confidence.MEDIUM, "시판 고추장 베이스로 추정", 1, null);
        haggled(c, "라면", "1봉지", 80, "면 전체가 밀 — 봉지라면 1인분", "반봉지 + 계란", 40, 4, 0);
        promisePending(c, "치킨", "1마리", 70, "튀김옷이 밀가루 — 프라이드 기준", Weekday.FRI);
        checkin(c, 3, ConditionValue.BAD, ConditionValue.MID, ConditionValue.BAD);
        checkin(c, 2, ConditionValue.MID, ConditionValue.GOOD, ConditionValue.MID);
        checkin(c, 1, ConditionValue.GOOD, ConditionValue.GOOD, ConditionValue.GOOD);
        return finalize(c);
    }

    /** W1 완주 — 총소비 80, 체크인 6/7일, 40+ 섭취 다음날 BLOAT=BAD (리포트 발견 재료) */
    private Challenge completed(String userId) {
        Challenge c = challenge(userId, Period.W1, 85, 8, ChallengeStatus.COMPLETED);
        haggledRecorded(c, "라면", "1봉지", 80, "면 전체가 밀 — 봉지라면 1인분", "반봉지 + 계란", 40, 4, 6);
        record(c, "김밥", "1줄", 20, Confidence.HIGH, "단무지·어묵에 소량", 5, null);
        record(c, "제육볶음", "1인분", 15, Confidence.MEDIUM, "시판 고추장 베이스로 추정", 4, null);
        record(c, "된장찌개", "1그릇", 5, Confidence.HIGH, "된장에 미량 — 시판 된장 기준", 2, null);
        // 체크인 6/7 — 40 섭취(D-6) 다음날(D-5)만 BLOAT=BAD
        checkin(c, 7, ConditionValue.GOOD, ConditionValue.GOOD, ConditionValue.GOOD);
        checkin(c, 5, ConditionValue.BAD, ConditionValue.MID, ConditionValue.BAD);
        checkin(c, 4, ConditionValue.MID, ConditionValue.GOOD, ConditionValue.MID);
        checkin(c, 3, ConditionValue.GOOD, ConditionValue.GOOD, ConditionValue.GOOD);
        checkin(c, 2, ConditionValue.GOOD, ConditionValue.GOOD, ConditionValue.MID);
        checkin(c, 1, ConditionValue.GOOD, ConditionValue.GOOD, ConditionValue.GOOD);
        c.setCompletedAt(Instant.now());
        return finalize(c);
    }

    /** W4 12일차 — 잔액 240 (명세 §14.5 일치) */
    private Challenge w4Day12(String userId) {
        Challenge c = challenge(userId, Period.W4, 300, 11, ChallengeStatus.ACTIVE);
        record(c, "김밥", "1줄", 20, Confidence.HIGH, "단무지·어묵에 소량", 8, null);
        record(c, "떡볶이", "1인분", 55, Confidence.HIGH, "떡·어묵에 밀 혼합", 5, "반인분 + 어묵|25");
        record(c, "제육볶음", "1인분", 15, Confidence.MEDIUM, "시판 고추장 베이스로 추정", 2, null);
        return finalize(c);
    }

    /** 잔액 5 — 양 조절로도 초과를 못 피하는 REDUCE_OVERFLOW 시연 (명세 §14.6) */
    private Challenge lowBalance(String userId) {
        Challenge c = challenge(userId, Period.W1, 85, 2, ChallengeStatus.ACTIVE);
        record(c, "칼국수", "1그릇", 80, Confidence.CERTAIN, "면 전체가 밀 — 기준 앵커 메뉴", 1, null);
        return finalize(c);
    }

    /** 어제 흥정한 미기록 항목 1건 — 만료 확인 시트 시연 (명세 §6.8) */
    private Challenge expiredConfirm(String userId) {
        Challenge c = challenge(userId, Period.W1, 85, 1, ChallengeStatus.ACTIVE);
        LocalDate yesterday = LogicalDate.of(Instant.now()).minusDays(1);
        Item item = base(c, ItemKind.MEAL, "라면", "1봉지", 80, Confidence.CERTAIN, "면 전체가 밀 — 봉지라면 1인분");
        item.setAdjustedLabel("반봉지 + 계란");
        item.setAdjustedPoints(40);
        item.setAdjustedBasis("면 절반 + 계란 1");
        item.setAdjustedTurns(4);
        item.setStatus(ItemStatus.EXPIRED);
        item.setLogicalDate(yesterday);
        item.setExpiresAt(LogicalDate.expiryOf(yesterday));
        item.setExpiredAt(LogicalDate.expiryOf(yesterday));
        itemRepository.save(item);
        return finalize(c);
    }

    // ---- 빌더 ----

    private Challenge challenge(String userId, Period period, int budget, int startDaysAgo, ChallengeStatus status) {
        LocalDate startDate = LogicalDate.of(Instant.now()).minusDays(startDaysAgo);
        Instant startedAt = startDate.atTime(LocalTime.NOON).atZone(LogicalDate.KST).toInstant();

        Challenge c = new Challenge();
        c.setId(Ids.next(Ids.Prefix.CHALLENGE));
        c.setUserId(userId);
        c.setPeriod(period);
        c.setStatus(status);
        c.setTotalDays(period.totalDays());
        c.setNeedsSurvey(false);
        c.setSurveyNoodle(SurveyLevel.MID);
        c.setSurveyBread(SurveyLevel.LOW);
        c.setSurveySnack(SurveyLevel.HIGH);
        c.setOptionKey(OptionKey.CUSTOM);
        c.setBudget(budget);
        c.setBalance(budget);
        c.setEstimatedWeekly(100);
        c.setCutRatePercent(15);
        c.setStartTipText("면이 주식이군요. 라면 한 번을 반봉지+계란으로 바꾸는 것부터 시작해보세요.");
        c.setStartedAt(startedAt);
        c.setEndsAt(startedAt.plus(period.totalDays(), ChronoUnit.DAYS).minusSeconds(1));
        c.setCreatedAt(startedAt);
        runningBalance = budget;
        return challengeRepository.save(c);
    }

    private Item base(Challenge c, ItemKind kind, String name, String unit, int points,
                      Confidence confidence, String basis) {
        Item item = new Item();
        item.setId(Ids.next(Ids.Prefix.ITEM));
        item.setUserId(c.getUserId());
        item.setChallengeId(c.getId());
        item.setKind(kind);
        item.setSourceType(SourceType.TEXT);
        item.setOriginalName(name);
        item.setOriginalUnit(unit);
        item.setOriginalPoints(points);
        item.setOriginalPm(points == 0 ? 0 : 10);
        item.setOriginalConfidence(confidence);
        item.setOriginalBasis(basis);
        item.setLogicalDate(LogicalDate.of(Instant.now()));
        item.setCreatedAt(Instant.now());
        return item;
    }

    /** daysAgo일 전에 기록된 항목. adjusted 형식: "라벨|포인트" (null이면 원본 그대로) */
    private void record(Challenge c, String name, String unit, int points, Confidence confidence,
                        String basis, int daysAgo, String adjusted) {
        Item item = base(c, ItemKind.MEAL, name, unit, points, confidence, basis);
        int effective = points;
        if (adjusted != null) {
            String[] parts = adjusted.split("\\|");
            item.setAdjustedLabel(parts[0]);
            item.setAdjustedPoints(Integer.parseInt(parts[1]));
            item.setAdjustedTurns(4);
            effective = item.getAdjustedPoints();
        }
        LocalDate date = LogicalDate.of(Instant.now()).minusDays(daysAgo);
        item.setLogicalDate(date);
        item.setExpiresAt(LogicalDate.expiryOf(date));
        item.setStatus(ItemStatus.RECORDED);
        item.setRecordedAt(date.atTime(19, 0).atZone(LogicalDate.KST).toInstant());
        item.snapshotBalances(runningBalance, runningBalance - effective, runningBalance - points);
        runningBalance -= effective;
        itemRepository.save(item);
    }

    private void haggledRecorded(Challenge c, String name, String unit, int points, String basis,
                                 String adjLabel, int adjPoints, int turns, int daysAgo) {
        record(c, name, unit, points, Confidence.CERTAIN, basis, daysAgo, adjLabel + "|" + adjPoints);
    }

    /** 흥정 완료·미기록 항목 (3b 리스트 시연) */
    private void haggled(Challenge c, String name, String unit, int points, String basis,
                         String adjLabel, int adjPoints, int turns, int daysAgo) {
        Item item = base(c, ItemKind.MEAL, name, unit, points, Confidence.CERTAIN, basis);
        item.setAdjustedLabel(adjLabel);
        item.setAdjustedPoints(adjPoints);
        item.setAdjustedBasis("면 절반 + 계란 1");
        item.setAdjustedTurns(turns);
        item.setStatus(ItemStatus.HAGGLED);
        LocalDate date = LogicalDate.of(Instant.now()).minusDays(daysAgo);
        item.setLogicalDate(date);
        item.setExpiresAt(LogicalDate.expiryOf(date));
        itemRepository.save(item);
    }

    /** 약속 대기 항목 (3a 리스트 시연 — 선차감 전) */
    private void promisePending(Challenge c, String name, String unit, int points, String basis, Weekday weekday) {
        Item item = base(c, ItemKind.PROMISE, name, unit, points, Confidence.HIGH, basis);
        item.setStatus(ItemStatus.PENDING);
        item.setWeekday(weekday);
        itemRepository.save(item);
    }

    private void checkin(Challenge c, int daysAgo, ConditionValue bloat, ConditionValue skin, ConditionValue drowsy) {
        LocalDate date = LogicalDate.of(Instant.now()).minusDays(daysAgo);
        Checkin checkin = new Checkin();
        checkin.setId(Ids.next(Ids.Prefix.CHECKIN));
        checkin.setChallengeId(c.getId());
        checkin.setUserId(c.getUserId());
        checkin.setDate(date);
        checkin.setDayIndex((int) ChronoUnit.DAYS.between(LogicalDate.of(c.getStartedAt()), date) + 1);
        checkin.setBloat(bloat);
        checkin.setSkin(skin);
        checkin.setDrowsy(drowsy);
        Instant at = date.atTime(22, 0).atZone(LogicalDate.KST).toInstant();
        checkin.setCreatedAt(at);
        checkin.setUpdatedAt(at);
        checkinRepository.save(checkin);
    }

    private Challenge finalize(Challenge c) {
        c.setSpent(c.getBudget() - runningBalance);
        c.setBalance(runningBalance);
        return c;
    }
}
