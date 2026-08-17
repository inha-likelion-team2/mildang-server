package app.mildang.auth;

/** demo는 통과, prod는 카카오 JWKS로 서명·iss·aud·exp 검증 ({@link ProdKakaoVerifier}, 명세 §14.7 #1). */
public interface KakaoVerifier {

    /** @return provider 측 사용자 키 (실서비스: OIDC sub 클레임) */
    String verify(String idToken);
}
