package app.mildang.auth;

import app.mildang.auth.AuthDtos.RefreshRequest;
import app.mildang.auth.AuthDtos.SocialLoginRequest;
import app.mildang.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}
