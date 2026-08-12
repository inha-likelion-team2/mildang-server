package app.mildang.common.auth;

import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Bearer 토큰 검증 — 통과 시 request attribute에 userId를 심는다. 예외 경로는 WebConfig에서 제외. */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTR = "mildang.userId";

    private final JwtProvider jwtProvider;

    public AuthInterceptor(JwtProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        String userId = jwtProvider.parseUserId(header.substring("Bearer ".length()));
        request.setAttribute(USER_ID_ATTR, userId);
        return true;
    }
}
