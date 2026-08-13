package app.mildang.report;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByChallengeId(String challengeId);

    Optional<Report> findByInviteCode(String inviteCode);
}
