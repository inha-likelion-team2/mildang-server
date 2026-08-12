package app.mildang.auth;

import app.mildang.auth.AuthDtos.SocialLoginRequest;
import app.mildang.auth.AuthDtos.TokenResponse;
import app.mildang.auth.AuthDtos.UserView;
import app.mildang.common.auth.JwtProvider;
import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import app.mildang.common.util.Hashes;
import app.mildang.user.User;
import app.mildang.user.UserRepository;
import app.mildang.user.UserSession;
import app.mildang.user.UserSessionId;
import app.mildang.user.UserSessionRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final KakaoVerifier kakaoVerifier;
    private final JwtProvider jwtProvider;
    private final long refreshTtlDays;
    private final boolean demoEnabled;

    public AuthService(UserRepository userRepository, UserSessionRepository sessionRepository,
                       KakaoVerifier kakaoVerifier, JwtProvider jwtProvider, MildangProps props) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.kakaoVerifier = kakaoVerifier;
        this.jwtProvider = jwtProvider;
        this.refreshTtlDays = props.jwt().refreshTtlDays();
        this.demoEnabled = props.demo().enabled();
    }

    @Transactional
    public TokenResponse social(SocialLoginRequest request) {
        if (!"KAKAO".equals(request.provider())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "provider는 KAKAO만 지원해요.", "provider", null);
        }
        String providerSub = kakaoVerifier.verify(request.idToken());
        boolean[] created = {false};
        User user = userRepository.findByProviderSub(providerSub).orElseGet(() -> {
            created[0] = true;
            return createUser(providerSub);
        });
        user.setLastSeenAt(Instant.now());

        // 기기 = 세션. 같은 기기 재로그인이면 같은 행을 갱신한다 (스키마 v2.1 §4.2)
        Instant now = Instant.now();
        UserSession session = sessionRepository
                .findById(new UserSessionId(user.getId(), request.deviceId()))
                .orElseGet(() -> {
                    UserSession fresh = new UserSession();
                    fresh.setUserId(user.getId());
                    fresh.setDeviceId(request.deviceId());
                    fresh.setCreatedAt(now);
                    return fresh;
                });
        session.setPushToken(request.pushToken());
        session.setRevokedAt(null);
        String refreshToken = rotate(session, now);
        sessionRepository.save(session);

        return response(user, created[0], refreshToken);
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        UserSession session = sessionRepository.findByTokenHash(Hashes.sha256(refreshTokenValue))
                .filter(s -> s.getRevokedAt() == null && s.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        User user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        user.setLastSeenAt(Instant.now());

        String refreshToken = rotate(session, Instant.now()); // 같은 행 UPDATE — 새 행을 만들지 않는다
        return response(user, false, refreshToken);
    }

    /** @return 새 리프레시 토큰 평문 (저장은 SHA-256 해시만) */
    private String rotate(UserSession session, Instant now) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        session.setTokenHash(Hashes.sha256(token));
        session.setExpiresAt(now.plus(Duration.ofDays(refreshTtlDays)));
        session.setUpdatedAt(now);
        return token;
    }

    private User createUser(String providerSub) {
        Instant now = Instant.now();
        User user = new User();
        user.setId(Ids.next(Ids.Prefix.USER));
        user.setProvider("KAKAO");
        user.setProviderSub(providerSub);
        user.setNickname(nicknameFor(providerSub));
        user.setCreatedAt(now);
        user.setLastSeenAt(now);
        return userRepository.save(user);
    }

    /** demo 시드 계정(demo-judge-N)은 심사위원N, 그 외 게스트 4자리 (명세 §14.3·§14.5) */
    private String nicknameFor(String providerSub) {
        if (providerSub.startsWith("demo-judge-")) {
            String n = providerSub.substring("demo-judge-".length()).replaceFirst("^0+", "");
            return "심사위원" + (n.isEmpty() ? "1" : n);
        }
        return "게스트" + (1000 + RANDOM.nextInt(9000));
    }

    private TokenResponse response(User user, boolean isNew, String refreshToken) {
        return new TokenResponse(
                demoEnabled ? Boolean.TRUE : null,
                jwtProvider.createAccessToken(user.getId()),
                refreshToken,
                jwtProvider.accessTtlSeconds(),
                new UserView(user.getId(), user.getNickname(), isNew, user.isFreeTrialUsed()));
    }
}
