package app.mildang.tip;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardTipRepository extends JpaRepository<DashboardTip, String> {

    Optional<DashboardTip> findByChallengeIdAndDate(String challengeId, LocalDate date);

    List<DashboardTip> findTop3ByChallengeIdOrderByDateDesc(String challengeId);
}
