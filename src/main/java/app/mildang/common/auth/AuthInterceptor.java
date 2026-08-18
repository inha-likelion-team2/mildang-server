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
        // CORS preflight(OPTIONS)는 그냥 통과시킨다. 브라우저는 preflight에 Authorization을
        // 싣지 않으므로 여기서 토큰을 요구하면 «본 요청을 보내도 되는지» 묻는 단계에서 막혀
        // 다른 오리진의 FE가 보호된 경로를 아예 못 부른다.
        //
        // 게다가 preflight의 handler는 컨트롤러가 아니라 Spring 내부 PreFlightHandler라
        // ExceptionHandlerExceptionResolver가 @RestControllerAdvice를 찾지 못한다. 그래서
        // 여기서 던진 ApiException이 401이 아니라 «500 Internal Server Error»로 새어나갔다.
        if (org.springframework.web.cors.CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        String userId = jwtProvider.parseUserId(header.substring("Bearer ".length()));
        request.setAttribute(USER_ID_ATTR, userId);
        return true;
    }
}
