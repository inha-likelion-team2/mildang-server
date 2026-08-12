package app.mildang.challenge;

import app.mildang.challenge.ChallengeDtos.PlanCard;
import app.mildang.challenge.ChallengeDtos.PlansResponse;
import app.mildang.common.auth.JwtProvider;
import app.mildang.common.config.MildangProps;
import app.mildang.user.UserRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 화면 1 기간 카드 — 인증 불필요. 토큰이 있으면 freeTrialUsed를 반영한다 (명세 §3.1). */
@RestController
public class PlanController {

    private static final String NOTICE = "결제는 프리미엄이 아니라, 진짜 할 건지 확인하는 문턱이에요.";

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final boolean demoEnabled;

    public PlanController(UserRepository userRepository, JwtProvider jwtProvider, MildangProps props) {
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.demoEnabled = props.demo().enabled();
    }

    @GetMapping("/plans")
    public PlansResponse plans(@RequestHeader(value = "Authorization", required = false) String authorization) {
        boolean freeTrialUsed = resolveFreeTrialUsed(authorization);
        List<PlanCard> cards = Arrays.stream(Period.values())
                .map(period -> {
                    boolean unavailable = period.isFree() && freeTrialUsed;
                    return new PlanCard(period, period.title(), period.subtitle(), period.priceKrw(),
                            period == Period.W1, !unavailable,
                            unavailable ? "무료 체험을 이미 쓰셨어요" : null);
                })
                .toList();
        return new PlansResponse(cards, NOTICE);
    }

    private boolean resolveFreeTrialUsed(String authorization) {
        if (demoEnabled || authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        try {
            String userId = jwtProvider.parseUserId(authorization.substring("Bearer ".length()));
            return userRepository.findById(userId).map(u -> u.isFreeTrialUsed()).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }
}
