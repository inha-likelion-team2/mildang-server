package app.mildang.checkin;

import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeService;
import app.mildang.checkin.CheckinDtos.Answers;
import app.mildang.checkin.CheckinDtos.PutResponse;
import app.mildang.checkin.CheckinDtos.Question;
import app.mildang.checkin.CheckinDtos.ResponseRate;
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

    static final int REPORT_THRESHOLD = 60;

    private static final List<Question> QUESTIONS = List.of(
            new Question("BLOAT", "더부룩함", "식후 속 상태"),
            new Question("SKIN", "피부", "트러블·건조"),
            new Question("DROWSY", "낮 졸림", "식곤증 정도"));

    private final CheckinRepository checkinRepository;
    private final ChallengeService challengeService;

    public CheckinService(CheckinRepository checkinRepository, ChallengeService challengeService) {
        this.checkinRepository = checkinRepository;
        this.challengeService = challengeService;
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
                responseRate(challenge, now));
    }

    /** 멱등 — 같은 날 재호출 시 덮어쓴다 (명세 §8.2). 3문항 모두 필수는 DTO 검증에서. */
    @Transactional
    public PutResponse put(String userId, Answers answers) {
        Challenge challenge = challengeService.requireActive(userId);
        Instant now = Instant.now();
        LocalDate date = LogicalDate.of(now);

        Checkin checkin = checkinRepository.findByChallengeIdAndDate(challenge.getId(), date)
                .orElseGet(() -> {
                    Checkin fresh = new Checkin();
                    fresh.setId(Ids.next(Ids.Prefix.CHECKIN));
                    fresh.setChallengeId(challenge.getId());
                    fresh.setDate(date);
                    fresh.setCreatedAt(now);
                    return fresh;
                });
        checkin.setBloat(answers.BLOAT());
        checkin.setSkin(answers.SKIN());
        checkin.setDrowsy(answers.DROWSY());
        checkin.setUpdatedAt(now);
        checkinRepository.save(checkin);

        return new PutResponse(checkin.getId(), date.toString(), true, answers,
                "접수 완료. 오늘 장부는 닫습니다 — 내일 봬요.", responseRate(challenge, now));
    }

    private ResponseRate responseRate(Challenge challenge, Instant now) {
        int answered = (int) checkinRepository.countByChallengeId(challenge.getId());
        int total = challengeService.dayIndex(challenge, now);
        int percent = total == 0 ? 0 : Math.round(answered * 100f / total);
        return new ResponseRate(answered, total, percent, REPORT_THRESHOLD);
    }
}
