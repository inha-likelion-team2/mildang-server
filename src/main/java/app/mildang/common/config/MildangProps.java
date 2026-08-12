package app.mildang.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 프로필별 스위치와 비밀값 — 값은 application.yml + 환경변수에서 주입. */
@ConfigurationProperties(prefix = "mildang")
public record MildangProps(Demo demo, Jwt jwt, Ai ai) {

    public record Demo(boolean enabled) {
    }

    public record Jwt(String secret, long accessTtlSeconds, long refreshTtlDays) {
    }

    public record Ai(String baseUrl) {
    }
}
