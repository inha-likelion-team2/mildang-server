package app.mildang.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 프로필별 스위치와 비밀값 — 값은 application.yml + 환경변수에서 주입. */
/**
 * publicBaseUrl은 공유 딥링크에 찍히는 주소다 — 배포 도메인으로 넣지 않으면
 * 심사위원이 공유 카드 링크를 눌렀을 때 우리 서버가 아닌 곳으로 간다.
 */
@ConfigurationProperties(prefix = "mildang")
public record MildangProps(Demo demo, Jwt jwt, Ai ai, Kakao kakao, Toss toss, String publicBaseUrl) {

    public MildangProps {
        publicBaseUrl = publicBaseUrl != null ? publicBaseUrl : "http://localhost:8080";
        kakao = kakao != null ? kakao : new Kakao(null, null, null, null, null, null, null, null);
        toss = toss != null ? toss : new Toss(null, null, null, null);
    }

    public record Demo(boolean enabled) {
    }

    /**
     * 카카오 OIDC — prod에서 id_token을 검증할 때 쓴다.
     *
     * <p>appKey는 토큰의 {@code aud}와 대조할 값이라 <b>반드시 주입</b>해야 한다. 비어 있으면
     * «아무 문자열이나 계정이 되는» 상태가 되므로 prod 기동 시 즉시 실패시킨다.
     *
     * <p><b>콤마로 여러 개를 넣을 수 있다.</b> 카카오는 로그인 방식에 따라 {@code aud}에 들어가는 키가
     * 다르다 — JS SDK면 JavaScript 키, 서버가 인가 코드를 교환하면 REST API 키. 프론트 구현이
     * 바뀌거나 웹·앱을 같이 내면 하나만 넣어둔 쪽은 전 로그인이 막힌다.
     */
    public record Kakao(String appKey, String restApiKey, String clientSecret,
                        String issuer, String jwksUri, String tokenUri, String authorizeUri,
                        Duration timeout) {

        public Kakao {
            issuer = issuer != null ? issuer : "https://kauth.kakao.com";
            jwksUri = jwksUri != null ? jwksUri : "https://kauth.kakao.com/.well-known/jwks.json";
            tokenUri = tokenUri != null ? tokenUri : "https://kauth.kakao.com/oauth/token";
            authorizeUri = authorizeUri != null ? authorizeUri : "https://kauth.kakao.com/oauth/authorize";
            timeout = timeout != null ? timeout : Duration.ofSeconds(3);
        }

        /** aud로 인정할 앱 키들 — 하나라도 맞으면 우리 토큰이다 */
        public java.util.Set<String> audiences() {
            if (appKey == null || appKey.isBlank()) {
                return java.util.Set.of();
            }
            return java.util.Arrays.stream(appKey.split(","))
                    .map(String::trim).filter(key -> !key.isEmpty())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    /**
     * 토스페이먼츠 — 실결제.
     *
     * <p>clientKey는 브라우저가 결제창을 띄울 때 쓰고(노출돼도 되는 값), secretKey는 <b>서버만</b>
     * 승인 API에 쓴다. 시크릿이 없으면 결제 경로가 닫힌다 — 열어두면 «승인 없이 결제된 척»이 된다.
     */
    public record Toss(String clientKey, String secretKey, String confirmUri, Duration timeout) {

        public Toss {
            confirmUri = confirmUri != null ? confirmUri : "https://api.tosspayments.com/v1/payments/confirm";
            timeout = timeout != null ? timeout : Duration.ofSeconds(10);
        }
    }

    public record Jwt(String secret, long accessTtlSeconds, long refreshTtlDays) {
    }

    /**
     * fake=true면 AI-Server 없이 결정적 응답(FakeAiGateway) — 로컬·테스트용.
     * 타임아웃은 필수다: AI가 거절이 아니라 <b>멈추면</b> 호출이 트랜잭션 안이라 DB 커넥션까지 붙잡고,
     * 풀(기본 10)이 마르면 API 전체가 멈춘다. readTimeout은 가장 느린 호출(스캔 20초대) 기준.
     */
    public record Ai(String baseUrl, boolean fake, Duration connectTimeout, Duration readTimeout) {

        public Ai {
            connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
            readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(30);
        }
    }
}
