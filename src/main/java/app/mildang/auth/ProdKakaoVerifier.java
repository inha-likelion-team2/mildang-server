package app.mildang.auth;

import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 OIDC id_token 검증 (명세 §14.7 #1).
 *
 * <p>카카오 공개키(JWKS)로 서명을 확인하고 iss·aud·exp를 대조한 뒤 sub를 돌려준다.
 * 이 검증이 없으면 «아무 문자열이나 보내면 그게 계정이 되는» 상태라, 남의 sub를 적어 보내는 것만으로
 * 계정을 가로챌 수 있다. demo 프로필의 통과 동작({@link DemoKakaoVerifier})과 나뉘는 이유다.
 *
 * <p>공개키는 캐시하되 <b>모르는 kid가 오면 다시 받아온다</b> — 카카오가 키를 교체해도 로그인이
 * 끊기지 않게. 다만 위조 토큰이 임의의 kid를 달고 오면 매번 외부 호출을 유발할 수 있으므로
 * 재조회에 최소 간격을 둔다.
 */
@Component
@Profile("prod")
public class ProdKakaoVerifier implements KakaoVerifier {

    private static final Logger log = LoggerFactory.getLogger(ProdKakaoVerifier.class);

    /** 모르는 kid가 와도 이 간격 안에는 다시 받아오지 않는다 (위조 토큰으로 인한 호출 폭주 방지) */
    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(1);

    /** 기기 시계가 조금 어긋나도 방금 발급된 토큰이 거절되지 않게 */
    private static final long CLOCK_SKEW_SECONDS = 60;

    /** 테스트에서 네트워크 없이 JWKS를 갈아끼우기 위한 자리 */
    @FunctionalInterface
    interface JwksFetcher {
        String fetch();
    }

    private final MildangProps.Kakao config;
    private final JwksFetcher fetcher;
    private final Duration refreshInterval;
    private final ObjectMapper om = new ObjectMapper();

    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant lastFetch = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public ProdKakaoVerifier(MildangProps props) {
        this(props.kakao(), httpFetcher(props.kakao()), REFRESH_INTERVAL);
    }

    ProdKakaoVerifier(MildangProps.Kakao config, JwksFetcher fetcher) {
        this(config, fetcher, REFRESH_INTERVAL);
    }

    ProdKakaoVerifier(MildangProps.Kakao config, JwksFetcher fetcher, Duration refreshInterval) {
        this.config = config;
        this.fetcher = fetcher;
        this.refreshInterval = refreshInterval;
        if (config.appKey() == null || config.appKey().isBlank()) {
            // 여기서 죽이지 않으면 aud 대조를 건너뛰게 되고, 그건 검증하지 않는 것과 같다.
            throw new IllegalStateException(
                    "KAKAO_APP_KEY가 없습니다 — prod에서는 id_token의 aud를 대조할 앱 키가 반드시 필요합니다.");
        }
    }

    @Override
    public String verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        try {
            var claims = Jwts.parser()
                    .keyLocator(this::locate)
                    .requireIssuer(config.issuer())
                    .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();

            // aud는 여러 개일 수 있어 직접 대조한다 — 우리 앱 키가 들어 있어야만 우리 토큰이다
            Set<String> audience = claims.getAudience();
            if (audience == null || !audience.contains(config.appKey())) {
                throw new ApiException(ErrorCode.TOKEN_INVALID);
            }
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw new ApiException(ErrorCode.TOKEN_INVALID);
            }
            return sub;
        } catch (ExpiredJwtException e) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
    }

    /** kid로 공개키를 찾는다 — 없으면 한 번 더 받아본다 (카카오 키 교체 대응) */
    private Key locate(io.jsonwebtoken.Header header) {
        Object kid = header.get("kid");
        if (kid == null) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        PublicKey key = keys.get(kid.toString());
        if (key == null) {
            refresh();
            key = keys.get(kid.toString());
        }
        if (key == null) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        return key;
    }

    private synchronized void refresh() {
        if (Instant.now().isBefore(lastFetch.plus(refreshInterval))) {
            return; // 방금 받아왔다 — 모르는 kid는 위조로 본다
        }
        lastFetch = Instant.now();
        try {
            keys = parseJwks(fetcher.fetch());
        } catch (RuntimeException e) {
            // 카카오가 잠깐 안 되는 동안 기존 캐시로 계속 로그인을 받는 편이 낫다
            log.warn("카카오 JWKS 조회 실패 — 기존 캐시 {}개로 계속합니다", keys.size(), e);
        }
    }

    private Map<String, PublicKey> parseJwks(String json) {
        Map<String, PublicKey> parsed = new HashMap<>();
        JsonNode root = om.readTree(json);
        for (JsonNode jwk : root.path("keys")) {
            if (!"RSA".equals(jwk.path("kty").asString(""))) {
                continue; // 카카오는 RSA만 쓴다
            }
            String kid = jwk.path("kid").asString("");
            String n = jwk.path("n").asString("");
            String e = jwk.path("e").asString("");
            if (kid.isBlank() || n.isBlank() || e.isBlank()) {
                continue;
            }
            try {
                var decoder = Base64.getUrlDecoder();
                var spec = new RSAPublicKeySpec(
                        new BigInteger(1, decoder.decode(n)),
                        new BigInteger(1, decoder.decode(e)));
                parsed.put(kid, KeyFactory.getInstance("RSA").generatePublic(spec));
            } catch (Exception ex) {
                log.warn("JWKS 키 하나를 읽지 못했습니다 (kid={})", kid, ex);
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalStateException("JWKS에 쓸 수 있는 RSA 키가 없습니다.");
        }
        return Map.copyOf(parsed);
    }

    private static JwksFetcher httpFetcher(MildangProps.Kakao config) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
        return () -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(config.jwksUri()))
                        .timeout(config.timeout()).GET().build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("JWKS 응답 " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("JWKS 조회가 중단되었습니다.", e);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("JWKS 조회에 실패했습니다.", e);
            }
        };
    }
}
