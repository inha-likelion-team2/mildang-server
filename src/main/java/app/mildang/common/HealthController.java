package app.mildang.common;

import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배포 게이트가 이 응답 하나로 판정한다 — 실제 의존성을 확인해야 의미가 있다.
 * 하드코딩 ok는 커넥션 풀이 말라도 정상이라고 답해서, 가장 있을 법한 장애를 정확히 놓친다.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    /**
     * 지금 떠 있는 게 «어느 코드»인지. 배포가 실제로 붙었는지 밖에서 확인할 유일한 방법이다.
     *
     * <p>이게 없어서 두 번 막혔다 — Railway가 «성공»이라고 하는데 동작이 안 바뀌면,
     * 빌드가 실패한 건지 옛 이미지가 그대로 떠 있는 건지 응답만 봐서는 구분할 수 없다.
     * 커밋마다 손으로 올린다. 정확한 시각보다 «바뀌었는지»가 중요하다.
     */
    private static final String CODE_VERSION = "2026-08-19-completed-dashboard";

    private final JdbcTemplate jdbcTemplate;

    public HealthController(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        // 풀이 마르면 무한 대기 대신 빠르게 실패해야 헬스체크가 제 역할을 한다
        this.jdbcTemplate.setQueryTimeout(2);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException e) {
            log.error("health check failed — db unreachable", e);
            return ResponseEntity.status(503)
                    .body(Map.of("status", "down", "db", "unreachable", "codeVersion", CODE_VERSION));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "db", "ok", "codeVersion", CODE_VERSION));
    }
}
