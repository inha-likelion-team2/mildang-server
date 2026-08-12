package app.mildang.auth;

/** 실연동 교체 지점 #1 (명세 §14.7) — demo는 통과, prod는 JWKS 서명·aud·exp 검증. */
public interface KakaoVerifier {

    /** @return provider 측 사용자 키 (실서비스: OIDC sub 클레임) */
    String verify(String idToken);
}
