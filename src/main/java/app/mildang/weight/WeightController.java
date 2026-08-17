package app.mildang.weight;

import app.mildang.common.auth.CurrentUser;
import app.mildang.weight.WeightDtos.PutRequest;
import app.mildang.weight.WeightDtos.WeightResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/weights")
public class WeightController {

    private final WeightService weightService;

    public WeightController(WeightService weightService) {
        this.weightService = weightService;
    }

    /** 대시보드 그래프 재료 — 오늘 값 + 전체 시리즈 */
    @GetMapping
    public WeightResponse get(@CurrentUser String userId) {
        return weightService.get(userId);
    }

    /** 오늘 체중 — 하루 한 건, 멱등 덮어쓰기 */
    @PutMapping("/today")
    public WeightResponse putToday(@CurrentUser String userId, @Valid @RequestBody PutRequest request) {
        return weightService.putToday(userId, request.weightKg());
    }
}
