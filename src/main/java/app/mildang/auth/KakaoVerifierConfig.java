package app.mildang.auth;

import app.mildang.common.config.MildangProps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 카카오 검증기 선택 — <b>앱 키가 있으면 진짜, 없으면 데모</b>.
 *
 * <p>프로필로 나누면 로컬에서 진짜 카카오 토큰을 시험해 볼 수가 없다(prod로 띄우려면 실 DB가 필요).
 * 키 유무로 나누면 로컬 H2에서도 KAKAO_APP_KEY만 넣어 실검증을 확인할 수 있다.
 */
@Configuration
public class KakaoVerifierConfig {

    private static final Logger log = LoggerFactory.getLogger(KakaoVerifierConfig.class);

    @Bean
    public KakaoVerifier kakaoVerifier(MildangProps props) {
        if (!props.kakao().audiences().isEmpty()) {
            log.info("카카오 OIDC 실검증을 켭니다 (앱 키 {}개)", props.kakao().audiences().size());
            return new ProdKakaoVerifier(props);
        }
        log.warn("카카오 앱 키가 없어 검증을 건너뜁니다 — idToken 문자열이 그대로 계정이 됩니다 (데모 전용)");
        return new DemoKakaoVerifier();
    }
}
