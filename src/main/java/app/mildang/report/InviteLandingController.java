package app.mildang.report;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 공유 카드 딥링크가 실제로 열리는 자리.
 * 지금까지는 카드에 찍히는 주소를 받아줄 라우트가 없어 링크를 누르면 404였다 —
 * 획득 루프(스토리 → 링크 → 도전 받기)가 링크에서 끊겼다.
 * 사람이 브라우저로 여는 곳이라 JSON({@code GET /invites/{code}})이 아니라 페이지를 준다.
 */
@Controller
public class InviteLandingController {

    @GetMapping("/c/{code}")
    public String landing(@PathVariable String code) {
        // 코드는 페이지의 JS가 URL에서 다시 읽는다 (포워드해도 주소는 그대로 유지된다)
        return "forward:/invite/index.html";
    }
}
