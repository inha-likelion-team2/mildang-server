package app.mildang.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSession, UserSessionId> {
    Optional<UserSession> findByTokenHash(byte[] tokenHash);
}
