package app.mildang.checkin;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinRepository extends JpaRepository<Checkin, String> {

    Optional<Checkin> findByChallengeIdAndDate(String challengeId, LocalDate date);

    long countByChallengeId(String challengeId);

    java.util.List<Checkin> findByChallengeIdIn(java.util.List<String> challengeIds);
}
