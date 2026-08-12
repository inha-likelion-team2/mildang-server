package app.mildang.challenge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, String> {

    Optional<Challenge> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(String userId, List<ChallengeStatus> statuses);

    Optional<Challenge> findFirstByUserIdAndSurveyNoodleNotNullOrderByCreatedAtDesc(String userId);

    List<Challenge> findByUserId(String userId);

    List<Challenge> findByStatus(ChallengeStatus status);
}
