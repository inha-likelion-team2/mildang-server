package app.mildang.weight;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightRepository extends JpaRepository<WeightLog, String> {

    Optional<WeightLog> findByChallengeIdAndDate(String challengeId, LocalDate date);

    /** 대시보드 그래프용 — 1일차부터 순서대로 */
    List<WeightLog> findByChallengeIdOrderByDateAsc(String challengeId);
}
