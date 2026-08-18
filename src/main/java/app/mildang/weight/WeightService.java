package app.mildang.weight;

import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeService;
import app.mildang.common.id.Ids;
import app.mildang.common.time.LogicalDate;
import app.mildang.weight.WeightDtos.WeightPoint;
import app.mildang.weight.WeightDtos.WeightResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 체중 기록 — 값만 남긴다. 예산·잔액·리포트 통계에는 관여하지 않는다 (팀 결정 2026-08-17) */
@Service
public class WeightService {

    private final WeightRepository weightRepository;
    private final ChallengeService challengeService;

    public WeightService(WeightRepository weightRepository, ChallengeService challengeService) {
        this.weightRepository = weightRepository;
        this.challengeService = challengeService;
    }

    /** 오늘 체중 — 하루 한 건, 다시 보내면 덮어쓴다 (체크인과 같은 규칙) */
    @Transactional
    public WeightResponse putToday(String userId, BigDecimal weightKg) {
        Challenge challenge = challengeService.requireActive(userId);
        Instant now = Instant.now();
        LocalDate today = LogicalDate.of(now);
        BigDecimal value = weightKg.setScale(1, RoundingMode.HALF_UP);

        WeightLog log = weightRepository.findByChallengeIdAndDate(challenge.getId(), today)
                .orElseGet(() -> {
                    WeightLog created = new WeightLog();
                    created.setId(Ids.next(Ids.Prefix.WEIGHT));
                    created.setChallengeId(challenge.getId());
                    created.setUserId(userId);
                    created.setDate(today);
                    created.setDayIndex(challengeService.dayIndex(challenge, now));
                    created.setCreatedAt(now);
                    return created;
                });
        // 값을 채운 뒤에 저장한다 — weightKg가 NOT NULL이라 빈 채로 save하면 제약 위반
        log.setWeightKg(value);
        log.setUpdatedAt(now);
        weightRepository.save(log);

        return new WeightResponse(point(log), series(challenge.getId()));
    }

    @Transactional(readOnly = true)
    public WeightResponse get(String userId) {
        Challenge challenge = challengeService.requireActive(userId);
        LocalDate today = LogicalDate.of(Instant.now());
        WeightPoint todayPoint = weightRepository.findByChallengeIdAndDate(challenge.getId(), today)
                .map(WeightService::point).orElse(null);
        return new WeightResponse(todayPoint, series(challenge.getId()));
    }

    /** 그날의 체중 값 — 체크인 화면이 이미 넣은 값을 다시 보여줄 때 쓴다 (없으면 null) */
    @Transactional(readOnly = true)
    public BigDecimal todayValue(String challengeId, LocalDate date) {
        return weightRepository.findByChallengeIdAndDate(challengeId, date)
                .map(WeightLog::getWeightKg).orElse(null);
    }

    /**
     * 가장 최근에 잰 체중 (없으면 null). 체크인 화면 스테퍼의 <b>출발점</b>으로 쓴다 —
     * 출발점이 없으면 «+ 한 번 눌렀더니 60kg»처럼 엉뚱한 값이 기록된다.
     */
    @Transactional(readOnly = true)
    public BigDecimal latestValue(String challengeId) {
        List<WeightLog> logs = weightRepository.findByChallengeIdOrderByDateAsc(challengeId);
        return logs.isEmpty() ? null : logs.getLast().getWeightKg();
    }

    /** 대시보드 그래프용 — 챌린지 시작부터 기록된 날만, 날짜순 */
    public List<WeightPoint> series(String challengeId) {
        return weightRepository.findByChallengeIdOrderByDateAsc(challengeId).stream()
                .map(WeightService::point).toList();
    }

    private static WeightPoint point(WeightLog log) {
        return new WeightPoint(log.getDate().toString(), log.getDayIndex(), log.getWeightKg());
    }
}
