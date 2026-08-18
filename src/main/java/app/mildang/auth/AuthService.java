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
    private final KakaoTokenClient kakaoTokenClient;
    private final String restApiKey;
    private final String authorizeUri;
    private final JwtProvider jwtProvider;
    private final long refreshTtlDays;
    private final boolean demoEnabled;

    public AuthService(UserRepository userRepository, UserSessionRepository sessionRepository,
                       KakaoVerifier kakaoVerifier, KakaoTokenClient kakaoTokenClient,
                       JwtProvider jwtProvider, MildangProps props) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.kakaoVerifier = kakaoVerifier;
        this.kakaoTokenClient = kakaoTokenClient;
        this.jwtProvider = jwtProvider;
        this.refreshTtlDays = props.jwt().refreshTtlDays();
        this.demoEnabled = props.demo().enabled();
        this.restApiKey = props.kakao().restApiKey();
        this.authorizeUri = props.kakao().authorizeUri();
    }

    @Transactional
    public TokenResponse social(SocialLoginRequest request) {
        if (!"KAKAO".equals(request.provider())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "provider는 KAKAO만 지원해요.", "provider", null);
        }
        return issueFor(kakaoVerifier.verify(request.idToken()), request.deviceId(), request.pushToken());
    }

    /**
     * 웹 카카오 로그인 — 인가 코드를 서버가 토큰으로 바꾸고, 그 안의 id_token을 검증한다.
     * 여기서 얻는 sub가 곧 계정 식별자다 (닉네임·이메일은 받지 않는다).
     */
    @Transactional
    public TokenResponse kakaoLogin(AuthDtos.KakaoLoginRequest request) {
        String idToken = kakaoTokenClient.exchange(request.code(), request.redirectUri());
        return issueFor(kakaoVerifier.verify(idToken), request.deviceId(), request.pushToken());
    }

    /** 카카오에서 확인된 sub로 계정을 찾거나 만들고 토큰을 발급한다 (두 로그인 경로 공용) */
    private TokenResponse issueFor(String providerSub, String deviceId, String pushToken) {
        boolean[] created = {false};
        User user = userRepository.findByProviderSub(providerSub).orElseGet(() -> {
            created[0] = true;
            return createUser(providerSub);
        });
        user.setLastSeenAt(Instant.now());

        // 기기 = 세션. 같은 기기 재로그인이면 같은 행을 갱신한다 (스키마 v2.1 §4.2)
        Instant now = Instant.now();
        UserSession session = sessionRepository
                .findById(new UserSessionId(user.getId(), deviceId))
                .orElseGet(() -> {
                    UserSession fresh = new UserSession();
                    fresh.setUserId(user.getId());
                    fresh.setDeviceId(deviceId);
                    fresh.setCreatedAt(now);
                    return fresh;
                });
        session.setPushToken(pushToken);
        session.setRevokedAt(null);
        String refreshToken = rotate(session, now);
        sessionRepository.save(session);

        return response(user, created[0], refreshToken);
    }

    /**
     * 카카오 인가 화면 주소. scope=openid를 붙여야 id_token이 온다 — 이게 빠지면
     * 액세스 토큰만 와서 우리가 검증할 게 없다.
     */
    public AuthDtos.KakaoAuthorizeResponse authorizeUrl(String redirectUri) {
        if (restApiKey == null || restApiKey.isBlank()) {
            return new AuthDtos.KakaoAuthorizeResponse(null, false);
        }
        String url = authorizeUri
                + "?response_type=code"
                + "&client_id=" + java.net.URLEncoder.encode(restApiKey, java.nio.charset.StandardCharsets.UTF_8)
                + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8)
                + "&scope=openid";
        return new AuthDtos.KakaoAuthorizeResponse(url, true);
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
