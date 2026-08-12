package app.mildang.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

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
