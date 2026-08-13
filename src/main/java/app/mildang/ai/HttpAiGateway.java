package app.mildang.ai;

import app.mildang.ai.AiDtos.Estimate;
import app.mildang.ai.AiDtos.EstimateRequest;
import app.mildang.common.config.MildangProps;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** 같은 EC2의 FastAPI(AI-Server)를 내부 HTTP로 호출 — 외부 미노출 (BACKEND_NOTES §8) */
@Component
@ConditionalOnProperty(name = "mildang.ai.fake", havingValue = "false", matchIfMissing = true)
public class HttpAiGateway implements AiGateway {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HttpAiGateway(MildangProps props, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder()
                .baseUrl(props.ai().baseUrl())
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Estimate> estimateText(EstimateRequest request) {
        return call(() -> {
            Estimate[] result = restClient.post()
                    .uri("/internal/analyze-text")
                    .body(request)
                    .retrieve()
                    .body(Estimate[].class);
            return result == null ? List.of() : Arrays.asList(result);
        }, "analyze-text");
    }

    @Override
    public AiDtos.HaggleOutput haggleTurn(AiDtos.HaggleInput input) {
        return call(() -> restClient.post()
                .uri("/internal/haggle-turn")
                .body(input)
                .retrieve()
                .body(AiDtos.HaggleOutput.class), "haggle-turn");
    }

    @Override
    public AiDtos.ImageMenuAnalysisResult extractMenus(byte[] image) {
        return call(() -> restClient.post()
                .uri("/internal/analyze-image")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(image)
                .retrieve()
                .body(AiDtos.ImageMenuAnalysisResult.class), "analyze-image");
    }

    @Override
    public String scanComment(AiDtos.ScanCommentRequest request) {
        return call(() -> {
            // FastAPI response_model=str → 따옴표 포함 JSON 문자열
            String raw = restClient.post()
                    .uri("/internal/scan-comment")
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return raw == null ? null : objectMapper.readValue(raw, String.class);
        }, "scan-comment");
    }

    @Override
    public AiDtos.DashboardTip dashboardTip(AiDtos.DashboardTipRequest request) {
        return call(() -> restClient.post()
                .uri("/internal/dashboard-tip")
                .body(request)
                .retrieve()
                .body(AiDtos.DashboardTip.class), "dashboard-tip");
    }

    @Override
    public AiDtos.Finding finding(AiDtos.FindingRequest request) {
        return call(() -> restClient.post()
                .uri("/internal/finding")
                .body(request)
                .retrieve()
                .body(AiDtos.Finding.class), "finding");
    }

    private <T> T call(java.util.function.Supplier<T> supplier, String name) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new AiUnavailableException("AI-Server " + name + " 호출 실패", e);
        }
    }
}
