package app.mildang.auth;

import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** demo 사양(명세 §14.3): 검증 스킵, idToken 문자열을 그대로 계정 키로 사용. */
@Component
@Profile({"local", "demo"})
public class DemoKakaoVerifier implements KakaoVerifier {

    @Override
    public String verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
        return idToken;
    }
}
