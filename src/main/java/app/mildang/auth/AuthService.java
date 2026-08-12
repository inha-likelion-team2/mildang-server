package app.mildang.auth;

import app.mildang.auth.AuthDtos.SocialLoginRequest;
import app.mildang.auth.AuthDtos.TokenResponse;
import app.mildang.auth.AuthDtos.UserView;
import app.mildang.common.auth.JwtProvider;
import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import app.mildang.user.RefreshToken;
import app.mildang.user.RefreshTokenRepository;
import app.mildang.user.User;
import app.mildang.user.UserRepository;
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
    private final RefreshTokenRepository refreshTokenRepository;
    private final KakaoVerifier kakaoVerifier;
    private final JwtProvider jwtProvider;
    private final long refreshTtlDays;
    private final boolean demoEnabled;

    public AuthService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                       KakaoVerifier kakaoVerifier, JwtProvider jwtProvider, MildangProps props) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
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
        String providerKey = kakaoVerifier.verify(request.idToken());
        boolean[] created = {false};
        User user = userRepository.findByProviderKey(providerKey).orElseGet(() -> {
            created[0] = true;
            return createUser(providerKey);
        });
        user.setPushToken(request.pushToken());
        user.setDeviceId(request.deviceId());
        return issueTokens(user, created[0]);
    }

    @Transactional
    public TokenResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findById(refreshTokenValue)
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID));
        refreshTokenRepository.delete(stored);
        return issueTokens(user, false);
    }

    private User createUser(String providerKey) {
        User user = new User();
        user.setId(Ids.next(Ids.Prefix.USER));
        user.setProvider("KAKAO");
        user.setProviderKey(providerKey);
        user.setNickname(nicknameFor(providerKey));
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    /** demo 시드 계정(demo-judge-N)은 심사위원N, 그 외 게스트 4자리 (명세 §14.3·§14.5) */
    private String nicknameFor(String providerKey) {
        if (providerKey.startsWith("demo-judge-")) {
            String n = providerKey.substring("demo-judge-".length()).replaceFirst("^0+", "");
            return "심사위원" + (n.isEmpty() ? "1" : n);
        }
        return "게스트" + (1000 + RANDOM.nextInt(9000));
    }

    private TokenResponse issueTokens(User user, boolean isNew) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        RefreshToken refresh = new RefreshToken();
        refresh.setToken("rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        refresh.setUserId(user.getId());
        refresh.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshTtlDays)));
        refreshTokenRepository.save(refresh);

        return new TokenResponse(
                demoEnabled ? Boolean.TRUE : null,
                jwtProvider.createAccessToken(user.getId()),
                refresh.getToken(),
                jwtProvider.accessTtlSeconds(),
                new UserView(user.getId(), user.getNickname(), isNew, user.isFreeTrialUsed()));
    }
}
