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
                .excludePathPatterns("/auth/**", "/plans", "/health", "/demo/ping", "/error");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }
}
