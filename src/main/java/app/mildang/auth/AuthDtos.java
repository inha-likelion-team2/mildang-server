package app.mildang.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    /**
     * 웹 카카오 로그인 — 브라우저가 카카오에서 받아온 인가 코드를 그대로 넘긴다.
     * redirectUri는 인가 요청에 쓴 값과 <b>글자까지 같아야</b> 카카오가 교환을 받아준다.
     */
    public record KakaoLoginRequest(@jakarta.validation.constraints.NotBlank String code,
                                    @jakarta.validation.constraints.NotBlank String redirectUri,
                                    @jakarta.validation.constraints.NotBlank String deviceId,
                                    String pushToken) {
    }

    /** 프론트가 카카오 인가 화면 주소를 서버에서 받아간다 — 앱 키를 화면에 박아두지 않으려고 */
    public record KakaoAuthorizeResponse(String authorizeUrl, boolean configured) {
    }

    public record SocialLoginRequest(
            @NotBlank String provider,
            @NotBlank String idToken,
            @NotBlank String deviceId,
            String pushToken) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(
            Boolean mocked,
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserView user) {
    }

    public record UserView(String id, String nickname, boolean isNew, boolean freeTrialUsed) {
    }
}
