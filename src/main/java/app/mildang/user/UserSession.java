package app.mildang.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 기기 = 세션 — DB 스키마 v2.1 §4.2 (user_devices + refresh_tokens 통합).
 * 리프레시 토큰은 SHA-256 해시로만 저장, 회전 시 같은 행을 UPDATE.
 */
@Entity
@IdClass(UserSessionId.class)
@Getter
@Setter
@NoArgsConstructor
public class UserSession {

    @Id
    private String userId;

    @Id
    private String deviceId;

    @Column(nullable = false, unique = true)
    private byte[] tokenHash;

    private String pushToken;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
