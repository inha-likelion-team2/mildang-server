package app.mildang.ai;

import app.mildang.ai.AiDtos.Estimate;
import app.mildang.ai.AiDtos.EstimateRequest;
import app.mildang.common.config.MildangProps;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 같은 EC2의 FastAPI(AI-Server)를 내부 HTTP로 호출 — 외부 미노출 (BACKEND_NOTES §8) */
@Component
@ConditionalOnProperty(name = "mildang.ai.fake", havingValue = "false", matchIfMissing = true)
public class HttpAiGateway implements AiGateway {

    private final RestClient restClient;

    public HttpAiGateway(MildangProps props) {
        this.restClient = RestClient.builder()
                .baseUrl(props.ai().baseUrl())
                .build();
    }

    @Override
    public List<Estimate> estimateText(EstimateRequest request) {
        try {
            Estimate[] result = restClient.post()
                    .uri("/internal/analyze-text")
                    .body(request)
                    .retrieve()
                    .body(Estimate[].class);
            return result == null ? List.of() : Arrays.asList(result);
        } catch (Exception e) {
            throw new AiUnavailableException("AI-Server analyze-text 호출 실패", e);
        }
    }
}
