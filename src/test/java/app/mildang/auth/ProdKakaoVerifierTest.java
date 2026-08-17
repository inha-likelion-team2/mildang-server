package app.mildang.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 카카오 OIDC 검증 (§14.7 #1). 네트워크 없이 — RSA 키를 직접 만들어 JWKS를 흉내낸다.
 *
 * <p>여기서 확인하는 건 «우리가 발급하지 않은 토큰이 통과하는가»다. 통과하면 남의 sub를 적어
 * 보내는 것만으로 계정을 가로챌 수 있다.
 */
class ProdKakaoVerifierTest {

    private static final String APP_KEY = "test-app-key";
    private static final String ISSUER = "https://kauth.kakao.com";
    private static final String KID = "kakao-key-1";

    static KeyPair kakaoKeys;
    static KeyPair attackerKeys;
    /** 카카오가 교체하기 전에 쓰던 키 */
    static KeyPair previousKakaoKeys;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        kakaoKeys = gen.generateKeyPair();
        attackerKeys = gen.generateKeyPair();
        previousKakaoKeys = gen.generateKeyPair();
    }

    /** 카카오가 내려주는 모양의 JWKS */
    private static String jwks(KeyPair pair, String kid) {
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return """
                {"keys":[{"kty":"RSA","alg":"RS256","use":"sig","kid":"%s","n":"%s","e":"%s"}]}
                """.formatted(kid,
                enc.encodeToString(pub.getModulus().toByteArray()),
                enc.encodeToString(pub.getPublicExponent().toByteArray()));
    }

    private static MildangProps.Kakao config() {
        return new MildangProps.Kakao(APP_KEY, ISSUER, "https://unused", Duration.ofSeconds(3));
    }

    private static ProdKakaoVerifier verifier(ProdKakaoVerifier.JwksFetcher fetcher) {
        return new ProdKakaoVerifier(config(), fetcher);
    }

    /** 카카오가 발급한 것과 같은 모양의 id_token */
    private static String token(KeyPair signer, String kid, String sub, String aud,
                                String issuer, Instant expiry) {
        return Jwts.builder()
                .header().add("kid", kid).and()
                .subject(sub)
                .issuer(issuer)
                .audience().add(aud).and()
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expiry))
                .signWith(signer.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String validToken() {
        return token(kakaoKeys, KID, "kakao-sub-12345", APP_KEY, ISSUER,
                Instant.now().plusSeconds(600));
    }

    @Test
    @DisplayName("★ 카카오가 서명한 정상 토큰이면 sub를 돌려준다")
    void acceptsGenuineToken() {
        String sub = verifier(() -> jwks(kakaoKeys, KID)).verify(validToken());
        assertEquals("kakao-sub-12345", sub);
    }

    @Test
    @DisplayName("★ 서명 없는 문자열은 거절한다 — 예전엔 이게 그대로 계정이 됐다")
    void rejectsPlainString() {
        ProdKakaoVerifier v = verifier(() -> jwks(kakaoKeys, KID));
        for (String bad : new String[] {"weight-user", "judge-01", "", "a.b.c"}) {
            ApiException e = assertThrows(ApiException.class, () -> v.verify(bad));
            assertEquals(ErrorCode.TOKEN_INVALID, e.code());
        }
    }

    @Test
    @DisplayName("★ 남의 키로 서명한 위조 토큰은 거절한다")
    void rejectsForgedSignature() {
        String forged = token(attackerKeys, KID, "victim-sub", APP_KEY, ISSUER,
                Instant.now().plusSeconds(600));
        ApiException e = assertThrows(ApiException.class,
                () -> verifier(() -> jwks(kakaoKeys, KID)).verify(forged));
        assertEquals(ErrorCode.TOKEN_INVALID, e.code());
    }

    @Test
    @DisplayName("★ 다른 앱(aud)의 토큰은 거절한다 — 서명이 진짜여도")
    void rejectsOtherApp() {
        String otherApp = token(kakaoKeys, KID, "kakao-sub-12345", "someone-elses-app", ISSUER,
                Instant.now().plusSeconds(600));
        ApiException e = assertThrows(ApiException.class,
                () -> verifier(() -> jwks(kakaoKeys, KID)).verify(otherApp));
        assertEquals(ErrorCode.TOKEN_INVALID, e.code());
    }

    @Test
    @DisplayName("발급자가 카카오가 아니면 거절한다")
    void rejectsOtherIssuer() {
        String otherIssuer = token(kakaoKeys, KID, "kakao-sub-12345", APP_KEY,
                "https://evil.example.com", Instant.now().plusSeconds(600));
        ApiException e = assertThrows(ApiException.class,
                () -> verifier(() -> jwks(kakaoKeys, KID)).verify(otherIssuer));
        assertEquals(ErrorCode.TOKEN_INVALID, e.code());
    }

    @Test
    @DisplayName("만료된 토큰은 TOKEN_EXPIRED — 다시 로그인하라고 구분해서 알려준다")
    void expiredIsDistinguished() {
        // 시계 오차 허용(60초)보다 확실히 지난 시점
        String expired = token(kakaoKeys, KID, "kakao-sub-12345", APP_KEY, ISSUER,
                Instant.now().minusSeconds(3600));
        ApiException e = assertThrows(ApiException.class,
                () -> verifier(() -> jwks(kakaoKeys, KID)).verify(expired));
        assertEquals(ErrorCode.TOKEN_EXPIRED, e.code());
    }

    @Test
    @DisplayName("★ 카카오가 키를 교체하면 다시 받아와서 계속 로그인된다")
    void refetchesOnKeyRotation() {
        // 실제로는 재조회 간격(1분)이 지나야 다시 받아온다 — 여기선 간격을 0으로 두고 그 이후를 본다
        AtomicInteger fetches = new AtomicInteger();
        AtomicReference<String> published = new AtomicReference<>(jwks(previousKakaoKeys, "old-kid"));
        ProdKakaoVerifier v = new ProdKakaoVerifier(config(),
                () -> { fetches.incrementAndGet(); return published.get(); }, Duration.ZERO);

        // 교체 전 키로 로그인된다
        assertEquals("sub-before", v.verify(token(previousKakaoKeys, "old-kid", "sub-before",
                APP_KEY, ISSUER, Instant.now().plusSeconds(600))));
        assertEquals(1, fetches.get());

        // 카카오가 키를 갈아끼운다 — 새 kid가 달린 토큰이 들어온다
        published.set(jwks(kakaoKeys, KID));
        assertEquals("kakao-sub-12345", v.verify(validToken()));
        assertEquals(2, fetches.get(), "모르는 kid를 보고 다시 받아왔어야 한다");
    }

    @Test
    @DisplayName("★ 모르는 kid가 쏟아져도 재조회를 반복하지 않는다")
    void doesNotHammerOnUnknownKid() {
        AtomicInteger fetches = new AtomicInteger();
        ProdKakaoVerifier v = verifier(() -> {
            fetches.incrementAndGet();
            return jwks(kakaoKeys, KID);
        });

        for (int i = 0; i < 5; i++) {
            String unknown = token(attackerKeys, "unknown-kid-" + i, "sub", APP_KEY, ISSUER,
                    Instant.now().plusSeconds(600));
            assertThrows(ApiException.class, () -> v.verify(unknown));
        }
        // 최소 간격(1분) 안이라 첫 요청 때 한 번만 나간다
        assertEquals(1, fetches.get());
    }

    @Test
    @DisplayName("★ 앱 키를 여러 개 넣으면 어느 쪽으로 로그인해도 통과한다")
    void acceptsAnyConfiguredAppKey() {
        // 웹은 로그인 방식에 따라 aud가 갈린다 — JS SDK면 JavaScript 키, 서버 교환이면 REST API 키
        MildangProps.Kakao twoKeys = new MildangProps.Kakao(
                "javascript-key, rest-api-key", ISSUER, "https://unused", null);
        ProdKakaoVerifier v = new ProdKakaoVerifier(twoKeys, () -> jwks(kakaoKeys, KID));

        for (String key : new String[] {"javascript-key", "rest-api-key"}) {
            String token = token(kakaoKeys, KID, "kakao-sub-12345", key, ISSUER,
                    Instant.now().plusSeconds(600));
            assertEquals("kakao-sub-12345", v.verify(token), key + "로 발급된 토큰이 막혔다");
        }

        // 목록에 없는 앱은 여전히 거절
        String stranger = token(kakaoKeys, KID, "kakao-sub-12345", "someone-else", ISSUER,
                Instant.now().plusSeconds(600));
        assertThrows(ApiException.class, () -> v.verify(stranger));
    }

    @Test
    @DisplayName("★ 앱 키가 없으면 기동에서 막는다 — aud 대조를 건너뛴 채 뜨면 안 된다")
    void refusesToStartWithoutAppKey() {
        MildangProps.Kakao noKey = new MildangProps.Kakao(" ", ISSUER, "https://unused", null);
        assertThrows(IllegalStateException.class,
                () -> new ProdKakaoVerifier(noKey, () -> jwks(kakaoKeys, KID)));
    }
}
