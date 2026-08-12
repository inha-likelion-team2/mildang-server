package app.mildang.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** DB 스키마 v2.1 §4.1 — 기기·푸시 토큰은 user_sessions로 분리 */
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

    /** 카카오 sub 클레임. demo는 idToken 문자열 그대로 (명세 §14.3) */
    @Column(nullable = false, unique = true)
    private String providerSub;

    @Column(nullable = false)
    private String nickname;

    /** W1이 ACTIVE가 되는 시점(예산 확정)에 true — 온보딩 이탈자 보호 (스키마 §4.1) */
    @Column(nullable = false)
    private boolean freeTrialUsed;

    @Column(nullable = false)
    private boolean retryUsed;

    @Column(nullable = false)
    private Instant createdAt;

    /** 7일 미접속 → ABANDONED 판정 기준 (명세 §11.1) */
    @Column(nullable = false)
    private Instant lastSeenAt;

    private Instant deletedAt;
}
