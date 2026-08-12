package app.mildang.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** TODO 실연동: 카카오 공개키(JWKS)로 서명·aud·exp 검증 후 sub 반환 (명세 §14.7 #1). */
@Component
@Profile("prod")
public class ProdKakaoVerifier implements KakaoVerifier {

    @Override
    public String verify(String idToken) {
        throw new UnsupportedOperationException("카카오 OIDC 검증은 실연동 시 구현");
    }
}
