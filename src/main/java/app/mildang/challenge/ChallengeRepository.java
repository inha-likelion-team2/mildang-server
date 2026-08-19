package app.mildang.challenge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, String> {

    Optional<Challenge> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(String userId, List<ChallengeStatus> statuses);

    /** 최신 것부터 — 「하루 넘기기」가 아직 시작 전(startedAt null)인 껍데기를 건너뛰고 고를 때 쓴다 */
    List<Challenge> findByUserIdAndStatusInOrderByCreatedAtDesc(String userId, List<ChallengeStatus> statuses);

    Optional<Challenge> findFirstByUserIdAndSurveyNoodleNotNullOrderByCreatedAtDesc(String userId);

    List<Challenge> findByUserId(String userId);

    List<Challenge> findByStatus(ChallengeStatus status);
}
