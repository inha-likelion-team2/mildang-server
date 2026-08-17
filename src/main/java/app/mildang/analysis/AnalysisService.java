package app.mildang.analysis;

import app.mildang.ai.AiDtos.Estimate;
import app.mildang.ai.AiDtos.EstimateRequest;
import app.mildang.ai.AiGateway;
import app.mildang.analysis.AnalysisDtos.AnalyzeTextRequest;
import app.mildang.analysis.AnalysisDtos.AnalyzeTextResponse;
import app.mildang.analysis.AnalysisDtos.CandidateView;
import app.mildang.analysis.AnalysisDtos.MenuView;
import app.mildang.analysis.AnalysisDtos.RecentEntry;
import app.mildang.analysis.AnalysisDtos.RecentResponse;
import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeService;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /** 분석 캐시 수명 — 지나면 항목 생성 시 재분석 요구 (명세 §5.1) */
    private static final Duration ANALYSIS_TTL = Duration.ofMinutes(30);

    private final AiGateway aiGateway;
    private final AnalysisRepository analysisRepository;
    private final ChallengeService challengeService;
    private final ObjectMapper objectMapper;

    public AnalysisService(AiGateway aiGateway, AnalysisRepository analysisRepository,
                           ChallengeService challengeService, ObjectMapper objectMapper) {
        this.aiGateway = aiGateway;
        this.analysisRepository = analysisRepository;
        this.challengeService = challengeService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AnalyzeTextResponse analyzeText(String userId, AnalyzeTextRequest request) {
        Challenge challenge = challengeService.requireActive(userId);
        if (!challenge.getId().equals(request.context().challengeId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "진행 중인 챌린지가 아니에요.");
        }

        Estimate estimate = callWithRetry(request.query());

        Instant now = Instant.now();
        Analysis analysis = new Analysis();
        analysis.setId(Ids.next(Ids.Prefix.ANALYSIS));
        analysis.setUserId(userId);
        analysis.setChallengeId(challenge.getId());
        analysis.setQuery(request.query());
        analysis.setKind(request.context().kind());
        analysis.setResolved(estimate.resolved());
        analysis.setExpiresAt(now.plus(ANALYSIS_TTL));
        analysis.setCreatedAt(now);

        if (estimate.resolved()) {
            analysis.setName(estimate.name());
            analysis.setUnit(estimate.unit());
            analysis.setPoints(estimate.points());
            analysis.setPm(estimate.pm());
            analysis.setConfidence(estimate.confidence());
            analysis.setBasis(estimate.basis());
            analysisRepository.save(analysis);

            return new AnalyzeTextResponse(analysis.getId(), true,
                    new MenuView(estimate.name(), estimate.unit(), estimate.points(), estimate.pm(),
                            estimate.confidence(), estimate.basis()),
                    null, analysis.getExpiresAt());
        }

        // 식별 실패 — 후보 3개를 detail로 실어 422 (명세 §5.1)
        List<CandidateView> candidates = estimate.candidates().stream()
                .map(c -> new CandidateView(c.name(), c.points(), c.pm(), c.confidence()))
                .toList();
        analysis.setCandidatesJson(objectMapper.writeValueAsString(candidates));
        analysisRepository.save(analysis);

        List<Map<String, Object>> detail = objectMapper.convertValue(candidates, new TypeReference<>() {
        });
        throw new ApiException(ErrorCode.ANALYSIS_FAILED, ErrorCode.ANALYSIS_FAILED.defaultMessage(),
                null, Map.of("candidates", detail));
    }

    /** 검증 게이트(§15.8.2) — 위반·오류 시 재시도 1회 → 422/503 */
    private Estimate callWithRetry(String query) {
        EstimateRequest aiRequest = EstimateRequest.freetext(query);
        AiGateway.AiUnavailableException lastUnavailable = null;

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                List<Estimate> results = aiGateway.estimateText(aiRequest);
                if (results.size() == 1 && passesGate(results.get(0))) {
                    return results.get(0);
                }
                log.warn("analyze-text 검증 게이트 위반 (attempt {}): {}", attempt + 1, results);
            } catch (AiGateway.AiUnavailableException e) {
                log.warn("analyze-text AI 호출 실패 (attempt {})", attempt + 1, e);
                lastUnavailable = e;
            }
        }
        if (lastUnavailable != null) {
            throw new ApiException(ErrorCode.AI_UNAVAILABLE);
        }
        throw new ApiException(ErrorCode.ANALYSIS_FAILED, ErrorCode.ANALYSIS_FAILED.defaultMessage(),
                null, Map.of("candidates", List.of()));
    }

    /**
     * FREETEXT 게이트: points 0~999 · basis ≤40자 · unit 필수 · <b>기준 수량 명시</b>(§8 #12) ·
     * 실패면 후보 정확히 3개.
     */
    private boolean passesGate(Estimate e) {
        if (e.resolved()) {
            return e.name() != null && !e.name().isBlank()
                    && e.unit() != null && !e.unit().isBlank()
                    && e.points() != null && e.points() >= 0 && e.points() <= 999
                    && e.pm() != null && e.pm() >= 0
                    && e.confidence() != null
                    && e.basis() != null && e.basis().length() <= 40
                    && app.mildang.ai.AiGates.hasQuantityBasis(e.unit(), e.basis());
        }
        return e.candidates() != null && e.candidates().size() == 3;
    }

    @Transactional(readOnly = true)
    public RecentResponse recent(String userId) {
        Set<String> names = new LinkedHashSet<>();
        for (Analysis analysis : analysisRepository.findTop20ByUserIdAndResolvedTrueOrderByCreatedAtDesc(userId)) {
            names.add(analysis.getName());
            if (names.size() == 3) {
                break;
            }
        }
        return new RecentResponse(names.stream().map(RecentEntry::new).toList());
    }

    /** 항목 생성용 — 소유·resolved·미만료 검증 (만료 시 404, 명세 §6.3) */
    @Transactional(readOnly = true)
    public Analysis requireUsable(String userId, String analysisId) {
        Analysis analysis = analysisRepository.findById(analysisId)
                .filter(a -> a.getUserId().equals(userId))
                .filter(Analysis::isResolved)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "분석 결과를 찾을 수 없어요. 다시 분석해 주세요."));
        if (analysis.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "분석이 만료됐어요. 다시 분석해 주세요.");
        }
        return analysis;
    }
}
