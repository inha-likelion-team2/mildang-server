package app.mildang.analysis;

import app.mildang.analysis.AnalysisDtos.AnalyzeTextRequest;
import app.mildang.analysis.AnalysisDtos.AnalyzeTextResponse;
import app.mildang.analysis.AnalysisDtos.RecentResponse;
import app.mildang.common.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/text")
    public AnalyzeTextResponse analyzeText(@CurrentUser String userId,
                                           @Valid @RequestBody AnalyzeTextRequest request) {
        return analysisService.analyzeText(userId, request);
    }

    @GetMapping("/recent")
    public RecentResponse recent(@CurrentUser String userId) {
        return analysisService.recent(userId);
    }
}
