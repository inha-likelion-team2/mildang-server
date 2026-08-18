package app.mildang.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS preflight는 인증 없이 통과해야 한다.
 *
 * <p>브라우저는 preflight(OPTIONS)에 Authorization을 싣지 않는다. 인증 인터셉터가 이걸 막으면
 * 다른 오리진의 FE는 «본 요청을 보내도 되는지» 묻는 단계에서 걸려 보호된 경로를 아예 못 부른다.
 * 실제로 배포본에서 500이 나갔다 — preflight의 handler가 컨트롤러가 아니라 PreFlightHandler라
 * @RestControllerAdvice가 잡지 못하고 새어나간 것이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsPreflightTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("보호된 경로의 preflight는 토큰 없이 200 — Authorization 헤더가 허용된다")
    void preflightOnProtectedPathPasses() throws Exception {
        mvc.perform(options("/challenges/current")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().stringValues("Access-Control-Allow-Headers",
                        "authorization, content-type"));
    }

    @Test
    @DisplayName("127.0.0.1 오리진도 통과 — localhost와 다른 오리진으로 취급된다")
    void preflightFromLoopbackIpPasses() throws Exception {
        mvc.perform(options("/items")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"));
    }

    @Test
    @DisplayName("preflight를 열어도 본 요청의 인증은 그대로 — 토큰 없는 GET은 401")
    void realRequestStillRequiresToken() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/challenges/current")
                        .header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized());
    }
}
