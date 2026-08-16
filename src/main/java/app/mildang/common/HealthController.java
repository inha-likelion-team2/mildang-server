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
            return ResponseEntity.status(503).body(Map.of("status", "down", "db", "unreachable"));
        }
        return ResponseEntity.ok(Map.of("status", "ok", "db", "ok"));
    }
}
