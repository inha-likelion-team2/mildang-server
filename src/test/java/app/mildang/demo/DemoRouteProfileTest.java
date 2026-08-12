package app.mildang.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 명세 §14.2 — demo/local에서는 /demo/* 활성. 컨텍스트 패스(/v1)는 MockMvc 경로에 포함하지 않는다. */
@SpringBootTest
@AutoConfigureMockMvc
class DemoRouteProfileTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("기본(local) 프로필에서는 /demo/* 가 열려 있고 mocked=true를 반환한다")
    void demoRouteIsOpen() throws Exception {
        mvc.perform(get("/demo/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mocked").value(true));
    }
}
