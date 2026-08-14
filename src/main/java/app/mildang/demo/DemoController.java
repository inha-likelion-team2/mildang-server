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

    public DemoController(DemoSeedService seedService, BatchJobs batchJobs,
                          ChallengeRepository challengeRepository, ChallengeService challengeService,
                          ItemRepository itemRepository, CheckinRepository checkinRepository) {
        this.seedService = seedService;
        this.batchJobs = batchJobs;
        this.challengeRepository = challengeRepository;
        this.challengeService = challengeService;
        this.itemRepository = itemRepository;
        this.checkinRepository = checkinRepository;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("mocked", true, "status", "pong");
    }

    public record SeedRequest(@NotNull String scenario) {
    }

    @PostMapping("/seed")
    public Map<String, Object> seed(@CurrentUser String userId, @RequestBody SeedRequest request) {
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

    /** 하루(N일)를 앞당긴다 — 챌린지·항목·체크인의 시각을 N일 과거로 이동 (명세 §14.6) */
    @PostMapping("/advance-day")
    @Transactional
    public Map<String, Object> advanceDay(@CurrentUser String userId, @RequestBody(required = false) AdvanceRequest request) {
        int days = request == null || request.days() == null ? 1 : request.days();
        Challenge challenge = challengeRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(
                        userId, List.of(ChallengeStatus.ONBOARDING, ChallengeStatus.ACTIVE))
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
        checkinRepository.findByChallengeIdIn(List.of(challenge.getId()))
                .forEach(c -> c.setDate(c.getDate().minusDays(days)));

        return Map.of("mocked", true,
                "dayIndex", challengeService.dayIndex(challenge, Instant.now()),
                "status", challenge.getStatus().name());
    }

    public record RunBatchRequest(@NotNull List<String> jobs) {
    }

    /** 배치 즉시 실행 — 스케줄러와 같은 코드를 호출한다 (명세 §14.6) */
    @PostMapping("/run-batch")
    public Map<String, Object> runBatch(@CurrentUser String userId, @RequestBody RunBatchRequest request) {
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
