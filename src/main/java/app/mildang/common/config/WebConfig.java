package app.mildang.common.config;

import app.mildang.common.auth.AuthInterceptor;
import app.mildang.common.auth.CurrentUserResolver;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(MildangProps.class)
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CurrentUserResolver currentUserResolver;

    public WebConfig(AuthInterceptor authInterceptor, CurrentUserResolver currentUserResolver) {
        this.authInterceptor = authInterceptor;
        this.currentUserResolver = currentUserResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 인증 불필요: /auth/*, GET /plans (명세 §0.2) + 헬스체크·데모 핑
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/**", "/plans", "/health", "/demo/ping", "/error",
                        "/invites/**", // 딥링크 랜딩은 로그인 전 진입 (§1)
                        "/tester/**"); // 테스트용 프론트 정적 파일 (실 프론트 완성 시 삭제)
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }

    /** 웹 FE(다른 오리진) 호출 허용 — 데모는 전체 오픈, 실서비스 전 FE 도메인으로 좁힐 것 */
    @Override
    public void addCorsMappings(org.springframework.web.servlet.config.annotation.CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
