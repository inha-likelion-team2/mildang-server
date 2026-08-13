package app.mildang.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 프로필별 스위치와 비밀값 — 값은 application.yml + 환경변수에서 주입. */
@ConfigurationProperties(prefix = "mildang")
public record MildangProps(Demo demo, Jwt jwt, Ai ai) {

    public record Demo(boolean enabled) {
    }

    public record Jwt(String secret, long accessTtlSeconds, long refreshTtlDays) {
    }

    /** fake=true면 AI-Server 없이 결정적 응답(FakeAiGateway) — 로컬·테스트용 */
    public record Ai(String baseUrl, boolean fake) {
    }
}
