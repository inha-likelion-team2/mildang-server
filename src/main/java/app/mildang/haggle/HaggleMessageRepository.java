package app.mildang.haggle;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HaggleMessageRepository extends JpaRepository<HaggleMessage, Long> {
    List<HaggleMessage> findBySessionIdOrderByIdAsc(String sessionId);
}
