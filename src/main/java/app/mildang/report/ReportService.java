package app.mildang.report;

import app.mildang.ai.AiDtos;
import app.mildang.ai.AiGates;
import app.mildang.ai.AiGateway;
import app.mildang.challenge.BudgetPolicy;
import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeRepository;
import app.mildang.challenge.ChallengeStatus;
import app.mildang.challenge.OptionKey;
import app.mildang.checkin.Checkin;
import app.mildang.checkin.CheckinRepository;
import app.mildang.checkin.ConditionValue;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.time.LogicalDate;
import app.mildang.item.Item;
import app.mildang.item.ItemRepository;
import app.mildang.item.ItemStatus;
import app.mildang.report.ReportDtos.Best;
import app.mildang.report.ReportDtos.ChallengeView;
import app.mildang.report.ReportDtos.Finding;
import app.mildang.report.ReportDtos.HaggleHighlight;
import app.mildang.report.ReportDtos.InviteResponse;
import app.mildang.report.ReportDtos.Metric;
import app.mildang.report.ReportDtos.NextChallenge;
import app.mildang.report.ReportDtos.ReportResponse;
import app.mildang.report.ReportDtos.Sample;
import app.mildang.report.ReportDtos.ShareCardRequest;
import app.mildang.report.ReportDtos.ShareCardResponse;
import app.mildang.report.ReportDtos.Stat;
import app.mildang.user.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 완주 리포트 — 통계는 백엔드 집계, 발견 문구만 write_finding() (명세 §9·§15.6) */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final int THRESHOLD = 40;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final String DISCLAIMER = "이 리포트는 의학적 진단이 아닌 본인 기록 기반 관찰입니다.";
    private static final Map<String, String> CONDITION_LABELS =
            Map.of("BLOAT", "더부룩함", "SKIN", "피부 트러블", "DROWSY", "낮 졸림");

    private final ReportRepository reportRepository;
    private final ChallengeRepository challengeRepository;
    private final ItemRepository itemRepository;
    private final CheckinRepository checkinRepository;
    private final UserRepository userRepository;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final boolean demoEnabled;
    private final String publicBaseUrl;

    public ReportService(ReportRepository reportRepository, ChallengeRepository challengeRepository,
                         ItemRepository itemRepository, CheckinRepository checkinRepository,
                         UserRepository userRepository, AiGateway aiGateway, ObjectMapper objectMapper,
                         app.mildang.common.config.MildangProps props) {
        this.reportRepository = reportRepository;
        this.challengeRepository = challengeRepository;
        this.itemRepository = itemRepository;
        this.checkinRepository = checkinRepository;
        this.userRepository = userRepository;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
        this.demoEnabled = props.demo().enabled();
        this.publicBaseUrl = props.publicBaseUrl();
    }

    @Transactional
    public ReportResponse get(String userId, String challengeId) {
        Challenge challenge = completedChallenge(userId, challengeId);
        Report report = reportRepository.findByChallengeId(challenge.getId())
                .orElseGet(() -> generate(challenge));
        return response(challenge, report);
    }

    /** 데모: 서버 렌더 대신 클라 캡처 — imageUrl 없이 딥링크·규격만 (명세 §14.1) */
    @Transactional
    public ShareCardResponse shareCard(String userId, String challengeId, ShareCardRequest request) {
        Challenge challenge = completedChallenge(userId, challengeId);
        Report report = reportRepository.findByChallengeId(challenge.getId())
                .orElseGet(() -> generate(challenge));
        List<String> mentions = request.mentions() == null ? List.of()
                : request.mentions().stream().limit(3).toList();
        report.setCardMentionsJson(objectMapper.writeValueAsString(mentions));
        report.setCardExpiresAt(Instant.now().plus(Duration.ofDays(30)));

        // mocked는 실제로 목일 때만 (다른 응답과 규칙을 맞춘다). 딥링크는 배포 도메인에서 온다 —
        // 하드코딩하면 공유 링크가 우리 서버가 아닌 곳을 가리켜 404가 난다.
        return new ShareCardResponse(demoEnabled ? Boolean.TRUE : null, report.getCardImageUrl(), 1080, 1920,
                publicBaseUrl + "/v1/c/" + report.getInviteCode(), "#밀가루흥정챌린지",
                report.getCardExpiresAt());
    }

    /** 딥링크 랜딩 — 무인증 (§9.3) */
    @Transactional(readOnly = true)
    public InviteResponse invite(String code) {
        Report report = reportRepository.findByInviteCode(code)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        Challenge challenge = challengeRepository.findById(report.getChallengeId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String nickname = userRepository.findById(challenge.getUserId())
                .map(u -> u.getNickname()).orElse("친구");
        return new InviteResponse(nickname, challenge.getPeriod(), report.getFindingHeadline(), "도전 받기");
    }

    // ---- 생성 ----

    private Challenge completedChallenge(String userId, String challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .filter(c -> c.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (challenge.getStatus() == ChallengeStatus.ACTIVE && challenge.getEndsAt() != null
                && Instant.now().isAfter(challenge.getEndsAt())) {
            challenge.setStatus(ChallengeStatus.COMPLETED);
            challenge.setCompletedAt(Instant.now());
        }
        if (challenge.getStatus() != ChallengeStatus.COMPLETED) {
            throw new ApiException(ErrorCode.CHALLENGE_NOT_COMPLETED);
        }
        return challenge;
    }

    private Report generate(Challenge challenge) {
        List<Item> recorded = itemRepository
                .findByChallengeIdAndStatusInOrderByCreatedAtDesc(challenge.getId(), List.of(ItemStatus.RECORDED));
        List<Checkin> checkins = checkinRepository.findByChallengeIdIn(List.of(challenge.getId()));

        Report report = new Report();
        report.setChallengeId(challenge.getId());
        report.setTitle("당신의 몸이 쓴 리포트");
        // 선차감도 예산에서 이미 빠져나간 돈이다. 빼먹으면 대시보드에서 150을 쓴 사용자가
        // 리포트에서는 「총 소비 80」을 보게 된다 — 같은 사실이 두 화면에서 다르게 보인다.
        int consumed = challenge.getSpent() + challenge.getPrepaid();
        report.setTotalSpent(consumed);
        report.setAnsweredDays(checkins.size());
        report.setTotalDays(challenge.getTotalDays());

        // VS_BUDGET = 남은 예산(대시보드 balance와 같은 값·같은 부호). 음수면 초과.
        int vs = challenge.getBudget() - consumed;
        List<Stat> stats = List.of(
                new Stat("TOTAL_SPENT", "총 소비", String.valueOf(consumed), "/" + challenge.getBudget()),
                new Stat("VS_BUDGET", "예산 대비", vs > 0 ? "+" + vs : String.valueOf(vs), null),
                new Stat("PEAK_SLOT", "최다 소비", peakSlot(recorded), null));
        report.setStatsJson(objectMapper.writeValueAsString(stats));

        // haggleHighlight — 선차감한 약속의 절감도 사용자가 실제로 아낀 것이다
        List<Item> settled = itemRepository.findByChallengeIdAndStatusInOrderByCreatedAtDesc(
                challenge.getId(), List.of(ItemStatus.RECORDED, ItemStatus.PREPAID));
        List<Item> haggled = settled.stream().filter(Item::isHaggled).toList();
        int totalSaved = haggled.stream().mapToInt(i -> i.getOriginalPoints() - i.getAdjustedPoints()).sum();
        report.setHaggleTotalSaved(totalSaved);
        haggled.stream()
                .max(java.util.Comparator.comparingInt(i -> i.getOriginalPoints() - i.getAdjustedPoints()))
                .ifPresent(best -> report.setHaggleBestItemId(best.getId()));
        report.setHaggleAvgTurns(round1(haggled.stream()
                .filter(i -> i.getAdjustedTurns() != null)
                .mapToInt(Item::getAdjustedTurns).average().orElse(0)));
        report.setHaggleLongestTurns(haggled.stream()
                .filter(i -> i.getAdjustedTurns() != null)
                .mapToInt(Item::getAdjustedTurns).max().orElse(0));

        // finding — 계산 가능성 기준 (고·저섭취군 각 1일 이상, §9.1)
        computeFinding(challenge, recorded, checkins, report);

        // nextChallenge — 한 단계 센 옵션, suggestedBudget은 주간값 (§9.1)
        OptionKey nextKey = challenge.getOptionKey() == OptionKey.EASY ? OptionKey.AS_IS : OptionKey.HARD;
        int estimatedWeekly = challenge.getEstimatedWeekly() != null ? challenge.getEstimatedWeekly() : 100;
        report.setNextPeriod(challenge.getPeriod().name());
        report.setNextOptionKey(nextKey.name());
        report.setNextSuggestedBudget(BudgetPolicy.weeklyBudget(estimatedWeekly, nextKey));

        report.setInviteCode(inviteCode());
        report.setGeneratedAt(Instant.now());
        return reportRepository.save(report);
    }

    /** 상관 계산 — 일별 섭취 합(임계 40) → 다음날 체크인 BAD율 비율. 0 분모는 +0.5 스무딩 [백엔드 결정] */
    private void computeFinding(Challenge challenge, List<Item> recorded, List<Checkin> checkins, Report report) {
        Map<LocalDate, Integer> dailyPoints = new HashMap<>();
        for (Item item : recorded) {
            dailyPoints.merge(item.getLogicalDate(), item.effectivePoints(), Integer::sum);
        }
        Map<LocalDate, Checkin> byDate = new HashMap<>();
        checkins.forEach(c -> byDate.put(c.getDate(), c));

        String bestKey = null;
        double bestRatio = 0;
        int[] bestGroups = null;
        for (Map.Entry<String, Function<Checkin, ConditionValue>> entry : Map.<String, Function<Checkin, ConditionValue>>of(
                "BLOAT", Checkin::getBloat, "SKIN", Checkin::getSkin, "DROWSY", Checkin::getDrowsy).entrySet()) {
            int highDays = 0;
            int highBad = 0;
            int lowDays = 0;
            int lowBad = 0;
            LocalDate start = LogicalDate.of(challenge.getStartedAt());
            for (int day = 0; day < challenge.getTotalDays(); day++) {
                LocalDate date = start.plusDays(day);
                Checkin next = byDate.get(date.plusDays(1));
                if (next == null) {
                    continue;
                }
                boolean bad = entry.getValue().apply(next) == ConditionValue.BAD;
                if (dailyPoints.getOrDefault(date, 0) >= THRESHOLD) {
                    highDays++;
                    highBad += bad ? 1 : 0;
                } else {
                    lowDays++;
                    lowBad += bad ? 1 : 0;
                }
            }
            if (highDays >= 1 && lowDays >= 1) {
                double ratio = round1(((highBad + 0.5) / (highDays + 1)) / ((lowBad + 0.5) / (lowDays + 1)));
                if (ratio > bestRatio) {
                    bestRatio = ratio;
                    bestKey = entry.getKey();
                    bestGroups = new int[]{highDays, highBad, lowDays, lowBad};
                }
            }
        }

        if (bestKey == null || bestRatio < 1.0) {
            report.setFindingAvailable(false);
            return;
        }
        report.setFindingAvailable(true);
        report.setFindingConditionKey(bestKey);
        report.setFindingThresholdPoints(THRESHOLD);
        report.setFindingRatio(bestRatio);

        AiDtos.Finding finding = findingWithRetry(challenge, report, bestGroups);
        report.setFindingHeadline(finding.headline());
        report.setFindingSampleNote(finding.sampleNote());
    }

    /** write_finding() 게이트: ratio 등장 + 인과·의학 어휘 금지 → 재시도 1회 → 템플릿 (§15.8.2) */
    private AiDtos.Finding findingWithRetry(Challenge challenge, Report report, int[] groups) {
        String ratioText = String.valueOf(report.getFindingRatio());
        AiDtos.FindingRequest request = new AiDtos.FindingRequest(
                new AiDtos.FindingStats(report.getFindingConditionKey(), THRESHOLD, report.getFindingRatio(),
                        new AiDtos.FindingGroup(groups[0], groups[1]),
                        new AiDtos.FindingGroup(groups[2], groups[3]),
                        report.getAnsweredDays(), report.getTotalDays()),
                new AiDtos.FindingChallenge(challenge.getPeriod().name(), challenge.getSpent(), challenge.getBudget()),
                new AiDtos.FindingHaggle(report.getHaggleTotalSaved(), report.getHaggleAvgTurns()));

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                AiDtos.Finding finding = aiGateway.finding(request);
                if (finding != null && finding.headline() != null && finding.headline().contains(ratioText)
                        && AiGates.cleanForFinding(finding.headline())
                        && finding.sampleNote() != null) {
                    return finding;
                }
                log.warn("finding 게이트 위반 (attempt {}): {}", attempt + 1, finding);
            } catch (AiGateway.AiUnavailableException e) {
                log.warn("finding 호출 실패 (attempt {})", attempt + 1, e);
            }
        }
        // 템플릿 폴백
        String label = CONDITION_LABELS.getOrDefault(report.getFindingConditionKey(), "더부룩함");
        return new AiDtos.Finding(
                "밀가루 " + THRESHOLD + "+ 섭취한 다음날, " + label + " 보고율 " + ratioText + "배",
                "응답 " + report.getAnsweredDays() + "/" + report.getTotalDays() + "일 · 표본이 작아 \"경향\"으로 읽어주세요");
    }

    private String peakSlot(List<Item> recorded) {
        Map<String, Integer> slots = new HashMap<>();
        for (Item item : recorded) {
            if (item.getRecordedAt() == null) {
                continue;
            }
            ZonedDateTime kst = item.getRecordedAt().atZone(LogicalDate.KST);
            String day = switch (kst.getDayOfWeek()) {
                case MONDAY -> "월";
                case TUESDAY -> "화";
                case WEDNESDAY -> "수";
                case THURSDAY -> "목";
                case FRIDAY -> "금";
                case SATURDAY -> "토";
                case SUNDAY -> "일";
            };
            String slot = kst.getHour() < 11 ? "아침" : kst.getHour() < 17 ? "점심" : "저녁";
            slots.merge(day + " " + slot, item.effectivePoints(), Integer::sum);
        }
        return slots.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("—");
    }

    private ReportResponse response(Challenge challenge, Report report) {
        List<Stat> stats = objectMapper.readValue(report.getStatsJson(), new TypeReference<>() {
        });

        Best best = null;
        if (report.getHaggleBestItemId() != null) {
            best = itemRepository.findById(report.getHaggleBestItemId())
                    .map(item -> new Best(item.getOriginalName(),
                            item.getOriginalUnit() + " " + item.getOriginalPoints(),
                            item.getAdjustedLabel() + " " + item.getAdjustedPoints(),
                            item.getOriginalPoints() - item.getAdjustedPoints(),
                            whenOf(item)))
                    .orElse(null);
        }

        Finding finding = report.isFindingAvailable()
                ? new Finding(true, report.getFindingHeadline(),
                        new Metric(report.getFindingConditionKey(), report.getFindingThresholdPoints(),
                                report.getFindingRatio()),
                        report.getFindingSampleNote(),
                        new Sample(report.getAnsweredDays(), report.getTotalDays()))
                : new Finding(false, null, null, null,
                        new Sample(report.getAnsweredDays(), report.getTotalDays()));

        int multiplier = challenge.getPeriod().weeks();
        int suggestedTotal = report.getNextSuggestedBudget() * multiplier;

        return new ReportResponse(
                new ChallengeView(challenge.getId(), challenge.getPeriod(),
                        challenge.getPeriod().label() + " 챌린지 · 완주", challenge.getCompletedAt()),
                report.getTitle(), stats, finding,
                new HaggleHighlight(report.getHaggleTotalSaved(), best,
                        report.getHaggleAvgTurns(), report.getHaggleLongestTurns()),
                DISCLAIMER,
                new NextChallenge(challenge.getPeriod(), report.getNextOptionKey(),
                        report.getNextSuggestedBudget(), "재대결 받기 · 이번엔 " + suggestedTotal));
    }

    private static String whenOf(Item item) {
        if (item.getRecordedAt() == null) {
            return "—";
        }
        ZonedDateTime kst = item.getRecordedAt().atZone(LogicalDate.KST);
        String day = switch (kst.getDayOfWeek()) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
        String slot = kst.getHour() < 11 ? "아침" : kst.getHour() < 17 ? "점심" : "저녁";
        return day + " " + slot;
    }

    private String inviteCode() {
        for (int i = 0; i < 10; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < 6; j++) {
                code.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            if (reportRepository.findByInviteCode(code.toString()).isEmpty()) {
                return code.toString();
            }
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
