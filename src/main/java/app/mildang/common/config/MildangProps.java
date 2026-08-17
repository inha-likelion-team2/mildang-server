package app.mildang.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 프로필별 스위치와 비밀값 — 값은 application.yml + 환경변수에서 주입. */
/**
 * publicBaseUrl은 공유 딥링크에 찍히는 주소다 — 배포 도메인으로 넣지 않으면
 * 심사위원이 공유 카드 링크를 눌렀을 때 우리 서버가 아닌 곳으로 간다.
 */
@ConfigurationProperties(prefix = "mildang")
public record MildangProps(Demo demo, Jwt jwt, Ai ai, Kakao kakao, String publicBaseUrl) {

    public MildangProps {
        publicBaseUrl = publicBaseUrl != null ? publicBaseUrl : "http://localhost:8080";
        kakao = kakao != null ? kakao : new Kakao(null, null, null, null);
    }

    public record Demo(boolean enabled) {
    }

    /**
     * 카카오 OIDC — prod에서 id_token을 검증할 때 쓴다.
     * appKey는 토큰의 aud와 대조할 값(카카오 앱의 REST API 키)이라 <b>반드시 주입</b>해야 한다.
     * 비어 있으면 «아무 문자열이나 계정이 되는» 상태가 되므로 prod 기동 시 즉시 실패시킨다.
     */
    public record Kakao(String appKey, String issuer, String jwksUri, Duration timeout) {

        public Kakao {
            issuer = issuer != null ? issuer : "https://kauth.kakao.com";
            jwksUri = jwksUri != null ? jwksUri : "https://kauth.kakao.com/.well-known/jwks.json";
            timeout = timeout != null ? timeout : Duration.ofSeconds(3);
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
