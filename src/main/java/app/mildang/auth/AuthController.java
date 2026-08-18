package app.mildang.auth;

import app.mildang.auth.AuthDtos.RefreshRequest;
import app.mildang.auth.AuthDtos.SocialLoginRequest;
import app.mildang.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/social")
    public TokenResponse social(@Valid @RequestBody SocialLoginRequest request) {
        return authService.social(request);
    }

    /** 웹 카카오 로그인 — 브라우저가 받아온 인가 코드를 넘기면 우리 토큰을 준다 */
    @PostMapping("/kakao")
    public TokenResponse kakao(@Valid @RequestBody AuthDtos.KakaoLoginRequest request) {
        return authService.kakaoLogin(request);
    }

    /** 카카오 인가 화면 주소 — 앱 키를 프론트에 박지 않으려고 서버가 만들어 준다 */
    @GetMapping("/kakao/authorize-url")
    public AuthDtos.KakaoAuthorizeResponse authorizeUrl(@RequestParam String redirectUri) {
        return authService.authorizeUrl(redirectUri);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}
