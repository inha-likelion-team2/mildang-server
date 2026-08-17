package app.mildang.checkin;

import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeService;
import app.mildang.checkin.CheckinDtos.Answers;
import app.mildang.checkin.CheckinDtos.CheckinDays;
import app.mildang.checkin.CheckinDtos.PutResponse;
import app.mildang.checkin.CheckinDtos.Question;
import app.mildang.checkin.CheckinDtos.TodayResponse;
import app.mildang.common.id.Ids;
import app.mildang.common.time.LogicalDate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinService {

    private static final List<Question> QUESTIONS = List.of(
            // 문구는 확정 와이어프레임 173:813 그대로
            new Question("BLOAT", "붓기", "얼굴 붓기 변화・체형 변화"),
            new Question("SKIN", "피부", "트러블 건조"),
            new Question("DROWSY", "식곤증", "식후 졸림 정도"));

    private final CheckinRepository checkinRepository;
    private final ChallengeService challengeService;
    private final app.mildang.weight.WeightService weightService;

    public CheckinService(CheckinRepository checkinRepository, ChallengeService challengeService,
                          app.mildang.weight.WeightService weightService) {
        this.checkinRepository = checkinRepository;
        this.challengeService = challengeService;
        this.weightService = weightService;
    }

    @Transactional(readOnly = true)
    public TodayResponse today(String userId) {
        Challenge challenge = challengeService.requireActive(userId);
        Instant now = Instant.now();
        LocalDate date = LogicalDate.of(now);
        Optional<Checkin> existing = checkinRepository.findByChallengeIdAndDate(challenge.getId(), date);

        return new TodayResponse(
                date.toString(),
                challengeService.dayIndex(challenge, now),
                existing.isPresent(),
                existing.map(c -> new Answers(c.getBloat(), c.getSkin(), c.getDrowsy())).orElse(null),
                QUESTIONS,
                checkinDays(challenge, now),
                weightService.todayValue(challenge.getId(), date));
    }

    /**
     * 멱등 — 같은 날 재호출 시 덮어쓴다 (명세 §8.2). 3문항 모두 필수는 DTO 검증에서.
     * 체중은 선택 — 보내면 같은 날짜의 체중 기록에 함께 남긴다 (화면 6에 체중 입력이 있다).
     */
    @Transactional
    public PutResponse put(String userId, Answers answers, java.math.BigDecimal weightKg) {
        Challenge challenge = challengeService.requireActive(userId);
        Instant now = Instant.now();
        LocalDate date = LogicalDate.of(now);

        Checkin checkin = checkinRepository.findByChallengeIdAndDate(challenge.getId(), date)
                .orElseGet(() -> {
                    Checkin fresh = new Checkin();
                    fresh.setId(Ids.next(Ids.Prefix.CHECKIN));
                    fresh.setChallengeId(challenge.getId());
                    fresh.setUserId(userId);
                    fresh.setDate(date);
                    fresh.setDayIndex(challengeService.dayIndex(challenge, now));
                    fresh.setCreatedAt(now);
                    return fresh;
                });
        checkin.setBloat(answers.BLOAT());
        checkin.setSkin(answers.SKIN());
        checkin.setDrowsy(answers.DROWSY());
        checkin.setUpdatedAt(now);
        checkinRepository.save(checkin);

        if (weightKg != null) {
            weightService.putToday(userId, weightKg);
        }
        return new PutResponse(checkin.getId(), date.toString(), true, answers,
                "접수 완료. 오늘 장부는 닫습니다 — 내일 봬요.", checkinDays(challenge, now),
                weightService.todayValue(challenge.getId(), date));
    }

    /** 명세 개정(§8.1) — 비율·임계 폐지, 원시 수치만: 기록한 날 / 경과일 / 총일수 */
    private CheckinDays checkinDays(Challenge challenge, Instant now) {
        int answered = (int) checkinRepository.countByChallengeId(challenge.getId());
        int elapsed = challengeService.dayIndex(challenge, now);
        return new CheckinDays(answered, elapsed, challenge.getTotalDays());
    }
}
