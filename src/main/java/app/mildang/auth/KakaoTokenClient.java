package app.mildang.auth;

import app.mildang.common.config.MildangProps;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 카카오 인가 코드 → 토큰 교환 (웹 로그인).
 *
 * <p>웹에서는 브라우저가 카카오로 갔다가 <code>?code=…</code>를 달고 우리 페이지로 돌아온다.
 * 그 코드를 <b>서버가</b> 토큰으로 바꾼다 — 브라우저에서 직접 부르면 CORS에 막히고,
 * client_secret을 쓰는 앱이면 그 값이 브라우저에 노출된다.
 *
 * <p>여기서 꺼내는 건 {@code id_token} 하나다. 액세스 토큰으로 프로필을 더 받아오지 않는다 —
 * 우리는 계정 식별자(sub)만 있으면 되고, 안 받는 정보는 지킬 필요도 없다.
 */
@Component
public class KakaoTokenClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoTokenClient.class);

    private final MildangProps.Kakao config;
    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    public KakaoTokenClient(MildangProps props) {
        this.config = props.kakao();
        this.http = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
    }

    /** @return id_token (JWT 문자열). 실패하면 TOKEN_INVALID */
    public String exchange(String code, String redirectUri) {
        if (config.restApiKey() == null || config.restApiKey().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "카카오 로그인이 아직 설정되지 않았어요.", "code", null);
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", config.restApiKey());
        form.put("redirect_uri", redirectUri);
        form.put("code", code);
        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            form.put("client_secret", config.clientSecret());
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.tokenUri()))
                    .timeout(config.timeout())
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(encode(form)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = om.readTree(response.body());
            if (response.statusCode() != 200) {
                // 카카오가 알려주는 사유는 로그에만 남긴다 — 사용자에겐 다시 로그인하라고만
                log.warn("카카오 토큰 교환 실패 {} — {}", response.statusCode(),
                        body.path("error_description").asString(""));
                throw new ApiException(ErrorCode.TOKEN_INVALID);
            }
            String idToken = body.path("id_token").asString("");
            if (idToken.isBlank()) {
                // OIDC를 안 켜면 access_token만 오고 id_token이 없다 — 콘솔 설정 문제다
                log.error("id_token이 없습니다 — 카카오 콘솔에서 OpenID Connect가 꺼져 있는지 확인하세요");
                throw new ApiException(ErrorCode.TOKEN_INVALID);
            }
            return idToken;
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        } catch (Exception e) {
            log.warn("카카오 토큰 교환 중 오류", e);
            throw new ApiException(ErrorCode.TOKEN_INVALID);
        }
    }

    private static String encode(Map<String, String> form) {
        StringBuilder out = new StringBuilder();
        form.forEach((k, v) -> {
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        });
        return out.toString();
    }
}
