package app.mildang.auth;

import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 카카오 검증기 두 자리.
 *
 * <p><b>{@code /auth/social}</b>(idToken 직접)은 demo에서 통과 검증기를 쓴다 — 심사위원 원탭
 * 로그인이 여기 걸려 있어서, 실 키를 넣었다고 이 길까지 막으면 시연이 멈춘다.
 *
 * <p><b>{@code /auth/kakao}</b>(인가 코드)는 <b>언제나 진짜 검증</b>이다. 카카오가 발급한 토큰만
 * 들어오는 길이라 통과시킬 이유가 없다. 키가 없으면 그 길만 닫힌다.
 */
@Configuration
public class KakaoVerifierConfig {

    private static final Logger log = LoggerFactory.getLogger(KakaoVerifierConfig.class);

    /** /auth/social 용 — demo면 통과, 아니면 실검증 */
    @Bean
    @Primary
    public KakaoVerifier kakaoVerifier(MildangProps props) {
        if (props.demo().enabled()) {
            log.warn("demo 로그인이 켜져 있습니다 — /auth/social 은 idToken 문자열을 그대로 계정으로 씁니다");
            return new DemoKakaoVerifier();
        }
        return realKakaoVerifier(props);
    }

    /** /auth/kakao 용 — 항상 진짜. 키가 없으면 호출 시점에 «설정되지 않았어요» */
    @Bean("realKakaoVerifier")
    public KakaoVerifier realKakaoVerifier(MildangProps props) {
        if (props.kakao().audiences().isEmpty()) {
            log.info("카카오 앱 키가 없어 카카오 로그인 경로는 닫힙니다 (데모 로그인만 동작)");
            return idToken -> {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "카카오 로그인이 아직 설정되지 않았어요.", null, null);
            };
        }
        log.info("카카오 OIDC 실검증을 켭니다 (앱 키 {}개)", props.kakao().audiences().size());
        return new ProdKakaoVerifier(props);
    }
}
