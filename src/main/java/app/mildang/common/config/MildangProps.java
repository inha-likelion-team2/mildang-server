package app.mildang.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 프로필별 스위치와 비밀값 — 값은 application.yml + 환경변수에서 주입. */
/**
 * publicBaseUrl은 공유 딥링크에 찍히는 주소다 — 배포 도메인으로 넣지 않으면
 * 심사위원이 공유 카드 링크를 눌렀을 때 우리 서버가 아닌 곳으로 간다.
 */
@ConfigurationProperties(prefix = "mildang")
public record MildangProps(Demo demo, Jwt jwt, Ai ai, String publicBaseUrl) {

    public MildangProps {
        publicBaseUrl = publicBaseUrl != null ? publicBaseUrl : "http://localhost:8080";
    }

    public record Demo(boolean enabled) {
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
