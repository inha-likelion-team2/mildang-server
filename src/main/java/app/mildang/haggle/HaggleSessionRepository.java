package app.mildang.haggle;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HaggleSessionRepository extends JpaRepository<HaggleSession, String> {

    Optional<HaggleSession> findFirstByItemIdAndStatus(String itemId, String status);

    List<HaggleSession> findTop10ByUserIdAndTargetNameAndStatusOrderByClosedAtDesc(
            String userId, String targetName, String status);
}
