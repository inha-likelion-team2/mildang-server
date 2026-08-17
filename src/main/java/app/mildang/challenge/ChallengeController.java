package app.mildang.challenge;

import app.mildang.challenge.ChallengeDtos.AdjustBudgetRequest;
import app.mildang.challenge.ChallengeDtos.ConfirmRequest;
import app.mildang.challenge.ChallengeDtos.ConfirmResponse;
import app.mildang.challenge.ChallengeDtos.CreateRequest;
import app.mildang.challenge.ChallengeDtos.CreateResponse;
import app.mildang.challenge.ChallengeDtos.CurrentResponse;
import app.mildang.challenge.ChallengeDtos.EstimateRequest;
import app.mildang.challenge.ChallengeDtos.EstimateResponse;
import app.mildang.common.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateResponse create(@CurrentUser String userId, @Valid @RequestBody CreateRequest request) {
        return challengeService.create(userId, request);
    }

    @PostMapping("/{id}/budget/estimate")
    public EstimateResponse estimate(@CurrentUser String userId, @PathVariable String id,
                                     @Valid @RequestBody EstimateRequest request) {
        return challengeService.estimate(userId, id, request.survey());
    }

    @PostMapping("/{id}/budget")
    public ConfirmResponse confirm(@CurrentUser String userId, @PathVariable String id,
                                   @Valid @RequestBody ConfirmRequest request) {
        return challengeService.confirm(userId, id, request);
    }

    /** 예산 조정 — 화면 «온보딩_03»의 "나중에도 언제든지 조정할 수 있어요" */
    @PatchMapping("/{id}/budget")
    public ConfirmResponse adjustBudget(@CurrentUser String userId, @PathVariable String id,
                                        @Valid @RequestBody AdjustBudgetRequest request) {
        return challengeService.adjustBudget(userId, id, request.budget());
    }

    @GetMapping("/current")
    public CurrentResponse current(@CurrentUser String userId) {
        return challengeService.current(userId);
    }
}
