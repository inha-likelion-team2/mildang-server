package app.mildang.demo;

import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 전용 라우트 — 명세 §14. prod 프로필에서는 빈 자체가 등록되지 않아 404가 된다.
 * seed / reset / advance-day / run-batch 는 도메인 서비스가 생기는 대로 여기에 추가.
 */
@RestController
@RequestMapping("/demo")
@Profile({"local", "demo"})
public class DemoController {

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of("mocked", true, "status", "pong");
    }
}
