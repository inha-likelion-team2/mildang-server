package app.mildang.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    private String id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false, unique = true)
    private String providerKey;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private boolean freeTrialUsed;

    @Column(nullable = false)
    private boolean retryUsed;

    private String pushToken;

    private String deviceId;

    @Column(nullable = false)
    private Instant createdAt;
}
