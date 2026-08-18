package app.mildang.common.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * https 프로필에서 <b>HTTP 8080도 같이</b> 연다.
 *
 * <p>공유 시트(Web Share API)는 HTTPS에서만 열리지만, 카카오 리다이렉트는 이미 http 주소로
 * 등록해 뒀고 자체 서명 인증서는 경고를 한 번 통과해야 한다. 둘 다 열어두면 상황에 맞는 쪽을
 * 골라 쓸 수 있다 — 개발 편의용이라 prod에는 켜지지 않는다.
 */
@Configuration
@Profile("https")
public class HttpAlsoConfig {

    private static final int HTTP_PORT = 8080;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpConnector() {
        return factory -> {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setPort(HTTP_PORT);
            connector.setScheme("http");
            connector.setSecure(false);
            factory.addAdditionalConnectors(connector);
        };
    }
}
