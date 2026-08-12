package app.mildang.batch;

import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeRepository;
import app.mildang.challenge.ChallengeStatus;
import app.mildang.common.time.LogicalDate;
import app.mildang.item.Item;
import app.mildang.item.ItemRepository;
import app.mildang.item.ItemStatus;
import app.mildang.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 3종 — 스케줄러(BatchScheduler)와 데모 수동 트리거(/demo/run-batch)가 같은 코드를 호출한다 (명세 §14.1).
 */
@Service
public class BatchJobs {

    private static final Logger log = LoggerFactory.getLogger(BatchJobs.class);
    private static final Duration EXPIRED_CONFIRM_WINDOW = Duration.ofHours(48);
    private static final Duration ABANDON_AFTER = Duration.ofDays(7);

    private final ItemRepository itemRepository;
    private final ChallengeRepository challengeRepository;
    private final UserRepository userRepository;

    public BatchJobs(ItemRepository itemRepository, ChallengeRepository challengeRepository,
                     UserRepository userRepository) {
        this.itemRepository = itemRepository;
        this.challengeRepository = challengeRepository;
        this.userRepository = userRepository;
    }

    public record ExpiryResult(int expired, int canceled) {
    }

    /**
     * 항목 만료 — 매일 05:00 KST (명세 §6.8).
     * PENDING → CANCELED(조용히) · HAGGLED → EXPIRED(확인 대기) · EXPIRED 48h 경과 → CANCELED.
     * 잔액은 건드리지 않는다 (스키마 §8.5).
     */
    @Transactional
    public ExpiryResult expireItems(Instant now) {
        int expired = 0;
        int canceled = 0;

        for (Item item : itemRepository.findByStatus(ItemStatus.PENDING)) {
            if (item.getExpiresAt() != null && !item.getExpiresAt().isAfter(now)) {
                item.setStatus(ItemStatus.CANCELED);
                item.setCanceledAt(now);
                canceled++;
            }
        }
        for (Item item : itemRepository.findByStatus(ItemStatus.HAGGLED)) {
            if (item.getExpiresAt() != null && !item.getExpiresAt().isAfter(now)) {
                item.setStatus(ItemStatus.EXPIRED);
                item.setExpiredAt(now);
                expired++;
            }
        }
        for (Item item : itemRepository.findByStatus(ItemStatus.EXPIRED)) {
            if (item.getExpiredAt() != null && !item.getExpiredAt().plus(EXPIRED_CONFIRM_WINDOW).isAfter(now)) {
                item.setStatus(ItemStatus.CANCELED);
                item.setCanceledAt(now);
                canceled++;
            }
        }
        log.info("item expiry batch: expired={}, canceled={}", expired, canceled);
        return new ExpiryResult(expired, canceled);
    }

    /**
     * 선차감 전환 — 매일 00:05 KST (명세 §6.5). 약속 요일이 지난 PREPAID → RECORDED.
     * 잔액 불변 — prepaid → spent 이동만 (§0.10 항등식).
     */
    @Transactional
    public int convertPrepaid(Instant now) {
        int converted = 0;
        LocalDate today = LogicalDate.of(now);

        for (Item item : itemRepository.findByStatus(ItemStatus.PREPAID)) {
            LocalDate target = LogicalDate.of(item.getPrepaidAt() != null ? item.getPrepaidAt() : item.getCreatedAt());
            while (target.getDayOfWeek() != item.getWeekday().dayOfWeek()) {
                target = target.plusDays(1);
            }
            if (today.isAfter(target)) {
                int effective = item.effectivePoints();
                item.setStatus(ItemStatus.RECORDED);
                item.setRecordedAt(now);
                challengeRepository.findById(item.getChallengeId()).ifPresent(c -> {
                    c.setPrepaid(c.getPrepaid() - effective);
                    c.setSpent(c.getSpent() + effective);
                });
                converted++;
            }
        }
        log.info("prepaid convert batch: converted={}", converted);
        return converted;
    }

    /** 챌린지 정리 — 기간 종료 → COMPLETED · 7일 미접속 → ABANDONED (명세 §11.1) */
    @Transactional
    public int closeChallenges(Instant now) {
        int changed = 0;
        for (Challenge challenge : challengeRepository.findByStatus(ChallengeStatus.ACTIVE)) {
            if (challenge.getEndsAt() != null && now.isAfter(challenge.getEndsAt())) {
                challenge.setStatus(ChallengeStatus.COMPLETED);
                challenge.setCompletedAt(now);
                changed++;
                continue;
            }
            boolean abandoned = userRepository.findById(challenge.getUserId())
                    .map(u -> u.getLastSeenAt().plus(ABANDON_AFTER).isBefore(now))
                    .orElse(false);
            if (abandoned) {
                challenge.setStatus(ChallengeStatus.ABANDONED);
                changed++;
            }
        }
        log.info("challenge close batch: changed={}", changed);
        return changed;
    }
}
