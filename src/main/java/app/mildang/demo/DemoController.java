package app.mildang.demo;

import app.mildang.batch.BatchJobs;
import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeRepository;
import app.mildang.challenge.ChallengeService;
import app.mildang.challenge.ChallengeStatus;
import app.mildang.checkin.CheckinRepository;
import app.mildang.common.auth.CurrentUser;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.item.Item;
import app.mildang.item.ItemRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 전용 라우트 — 명세 §14.6. prod 프로필에서는 빈 자체가 등록되지 않아 404.
 * /demo/ping 외에는 인증 필요 (WebConfig 제외 목록 참조).
 */
@RestController
@RequestMapping("/demo")
@Profile({"local", "demo"})
public class DemoController {

    private final DemoSeedService seedService;
    private final BatchJobs batchJobs;
    private final ChallengeRepository challengeRepository;
    private final ChallengeService challengeService;
    private final ItemRepository itemRepository;
    private final CheckinRepository checkinRepository;
    private final app.mildang.weight.WeightRepository weightRepository;

    public DemoController(DemoSeedService seedService, BatchJobs batchJobs,
                          ChallengeRepository challengeRepository, ChallengeService challengeService,
                          ItemRepository itemRepository, CheckinRepository checkinRepository,
                          app.mildang.weight.WeightRepository weightRepository) {
        this.seedService = seedService;
        this.batchJobs = batchJobs;
        this.challengeRepository = challengeRepository;
        this.challengeService = challengeService;
        this.itemRepository = itemRepository;
        this.checkinRepository = checkinRepository;
        this.weightRepository = weightRepository;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("mocked", true, "status", "pong");
    }

    public record SeedRequest(@NotNull String scenario) {
    }

    @PostMapping("/seed")
    public Map<String, Object> seed(@CurrentUser String userId, @Valid @RequestBody SeedRequest request) {
        Challenge challenge = seedService.seed(userId, request.scenario());
        Map<String, Object> body = new HashMap<>();
        body.put("mocked", true);
        body.put("scenario", request.scenario());
        body.put("challengeId", challenge == null ? null : challenge.getId());
        body.put("balance", challenge == null ? null : challenge.getBalance());
        return body;
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@CurrentUser String userId) {
        seedService.reset(userId);
    }

    public record AdvanceRequest(Integer days) {
    }

    /**
     * 하루(N일)를 앞당긴다 — 챌린지·항목·체크인의 시각을 N일 과거로 이동 (명세 §14.6).
     *
     * <p>⚠ COMPLETED도 대상에 넣는다. 예전엔 ONBOARDING·ACTIVE만 찾아서, 마지막 날을 넘기는 순간
     * 그다음부터 404가 났다 — 시연 중에 「하루 넘기기」가 영영 안 먹는 상태가 된다(FE 제보 2026-08-19).
     *
     * <p>⚠ 아직 시작 전(startedAt == null)인 챌린지는 건너뛴다. 완주 뒤에 새 챌린지를 만들어 두고
     * 예산을 정하기 전에 이 버튼을 누르면, 그 껍데기가 «가장 최근»이라 뽑혀 NPE로 500이 났다.
     */
    @PostMapping("/advance-day")
    @Transactional
    public Map<String, Object> advanceDay(@CurrentUser String userId, @RequestBody(required = false) AdvanceRequest request) {
        int days = request == null || request.days() == null ? 1 : request.days();
        Challenge challenge = challengeRepository
                .findByUserIdAndStatusInOrderByCreatedAtDesc(
                        userId, List.of(ChallengeStatus.ONBOARDING, ChallengeStatus.ACTIVE,
                                ChallengeStatus.COMPLETED))
                .stream()
                .filter(c -> c.getStartedAt() != null)
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "진행 중인 챌린지가 없어요."));

        challenge.setStartedAt(challenge.getStartedAt().minus(days, ChronoUnit.DAYS));
        challenge.setEndsAt(challenge.getEndsAt().minus(days, ChronoUnit.DAYS));
        for (Item item : itemRepository.findByChallengeIdIn(List.of(challenge.getId()))) {
            item.setLogicalDate(item.getLogicalDate().minusDays(days));
            item.setCreatedAt(item.getCreatedAt().minus(days, ChronoUnit.DAYS));
            if (item.getExpiresAt() != null) {
                item.setExpiresAt(item.getExpiresAt().minus(days, ChronoUnit.DAYS));
            }
            if (item.getRecordedAt() != null) {
                item.setRecordedAt(item.getRecordedAt().minus(days, ChronoUnit.DAYS));
            }
            if (item.getPrepaidAt() != null) {
                item.setPrepaidAt(item.getPrepaidAt().minus(days, ChronoUnit.DAYS));
            }
            if (item.getExpiredAt() != null) {
                item.setExpiredAt(item.getExpiredAt().minus(days, ChronoUnit.DAYS));
            }
        }
        for (app.mildang.checkin.Checkin checkin
                : inShiftOrder(checkinRepository.findByChallengeIdIn(List.of(challenge.getId())),
                        app.mildang.checkin.Checkin::getDate, days)) {
            checkin.setDate(checkin.getDate().minusDays(days));
            checkinRepository.saveAndFlush(checkin);
        }
        // 체중도 같이 옮긴다 — 안 옮기면 챌린지만 과거로 가고 체중은 제자리라
        // 진행률 카드의 일차별 체중이 어긋난다 (2026-08-18)
        for (app.mildang.weight.WeightLog weight
                : inShiftOrder(weightRepository.findByChallengeIdIn(List.of(challenge.getId())),
                        app.mildang.weight.WeightLog::getDate, days)) {
            weight.setDate(weight.getDate().minusDays(days));
            weightRepository.saveAndFlush(weight);
        }

        // 날짜를 옮겼으면 완주 여부도 여기서 확정한다. 안 그러면 마지막 날을 넘겼는데도
        // 「dayIndex 7 · ACTIVE」가 돌아와 «안 넘어갔다»로 보인다 — 전이는 다음 current 호출에서야
        // 일어났다(FE 제보 2026-08-19).
        Instant now = Instant.now();
        if (challenge.getStatus() == ChallengeStatus.ACTIVE
                && challenge.getEndsAt() != null && now.isAfter(challenge.getEndsAt())) {
            challenge.setStatus(ChallengeStatus.COMPLETED);
            challenge.setCompletedAt(now);
        }

        return Map.of("mocked", true,
                "challengeId", challenge.getId(),
                "dayIndex", challengeService.dayIndex(challenge, now),
                "status", challenge.getStatus().name(),
                "completed", challenge.getStatus() == ChallengeStatus.COMPLETED);
    }

    /**
     * 날짜를 옮길 행들을 «옮기는 방향»으로 정렬한다 — 뒤로 옮기면 이른 날짜부터, 앞으로 옮기면 늦은 날짜부터.
     *
     * <p>체크인·체중에는 {@code (challengeId, date)} 유니크 제약이 걸려 있고, 행마다 UPDATE가 따로 나간다.
     * 순서를 안 정하면 DB가 돌려준 물리적 순서 그대로 옮기다가 <b>중간 상태에서 아직 안 옮긴 행의 날짜와
     * 겹쳐</b> duplicate key로 트랜잭션이 통째로 롤백됐다 — 하루 넘기기가 그 뒤로 영영 500이 된다
     * (FE 제보 2026-08-20, 「식사를 기록하면 5일차부터 안 넘어감」). 이 순서로 옮기면 목적지 날짜가
     * 항상 먼저 비므로 겹칠 일이 없다.
     *
     * <p>H2(로컬·테스트)는 삽입 순서가 곧 날짜 순서라 이 버그가 드러나지 않는다. 배포본(Postgres)에서만
     * 며칠치가 쌓인 뒤에 터졌다 — 그래서 {@link #inShiftOrder}만 따로 단위 테스트로 못 박아 둔다.
     */
    static <T> List<T> inShiftOrder(List<T> rows, java.util.function.Function<T, java.time.LocalDate> dateOf,
                                    int days) {
        java.util.Comparator<T> byDate = java.util.Comparator.comparing(dateOf);
        List<T> ordered = new java.util.ArrayList<>(rows);
        ordered.sort(days >= 0 ? byDate : byDate.reversed());
        return ordered;
    }

    public record RunBatchRequest(@NotNull List<String> jobs) {
    }

    /** 배치 즉시 실행 — 스케줄러와 같은 코드를 호출한다 (명세 §14.6) */
    @PostMapping("/run-batch")
    public Map<String, Object> runBatch(@CurrentUser String userId, @Valid @RequestBody RunBatchRequest request) {
        Instant now = Instant.now();
        int converted = 0;
        int expired = 0;
        int canceled = 0;
        int closed = 0;
        for (String job : request.jobs()) {
            switch (job) {
                case "PREPAID_CONVERT" -> converted += batchJobs.convertPrepaid(now);
                case "ITEM_EXPIRY" -> {
                    BatchJobs.ExpiryResult result = batchJobs.expireItems(now);
                    expired += result.expired();
                    canceled += result.canceled();
                }
                case "CHALLENGE_CLOSE" -> closed += batchJobs.closeChallenges(now);
                default -> throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "jobs는 PREPAID_CONVERT / ITEM_EXPIRY / CHALLENGE_CLOSE 중에서 골라주세요.", "jobs", null);
            }
        }
        return Map.of("mocked", true, "converted", converted, "expired", expired, "canceled", canceled,
                "closed", closed);
    }
}
