package app.mildang.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 명세 §14.2 — prod에서 /demo/* 는 404. 데이터소스는 테스트용 H2로 덮어써 컨텍스트만 검증한다. */
@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        "spring.datasource.url=jdbc:h2:mem:prodtest;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "mildang.jwt.secret=test-secret-key-needs-32-bytes-min!!"
})
@AutoConfigureMockMvc
class DemoRouteProdAbsentTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("prod 프로필에서는 /demo/* 가 존재하지 않는다 (404)")
    void demoRouteIsAbsent() throws Exception {
        mvc.perform(get("/demo/ping")).andExpect(status().isNotFound());
    }
}
