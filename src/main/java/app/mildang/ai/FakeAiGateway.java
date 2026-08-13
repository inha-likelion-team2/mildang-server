package app.mildang.ai;

import app.mildang.ai.AiDtos.Candidate;
import app.mildang.ai.AiDtos.Estimate;
import app.mildang.ai.AiDtos.EstimateRequest;
import app.mildang.common.model.Confidence;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI-Server 없이 개발·테스트용 결정적 응답 (mildang.ai.fake=true).
 * 값은 명세 §15.2.4 참조 스케일과 프리셋 기준. 모르는 메뉴는 후보 3개로 unresolved.
 */
@Component
@ConditionalOnProperty(name = "mildang.ai.fake", havingValue = "true")
public class FakeAiGateway implements AiGateway {

    private record Known(String unit, int points, int pm, Confidence confidence, String basis) {
    }

    private static final Map<String, Known> KNOWN = Map.ofEntries(
            Map.entry("라면", new Known("1봉지", 80, 10, Confidence.CERTAIN, "면 전체가 밀 — 봉지라면 1인분 기준")),
            Map.entry("김밥", new Known("1줄", 20, 5, Confidence.HIGH, "단무지·어묵에 소량")),
            Map.entry("빵", new Known("1개", 45, 10, Confidence.HIGH, "밀가루 반죽 — 기본 1개 기준")),
            Map.entry("떡볶이", new Known("1인분", 55, 10, Confidence.HIGH, "떡·어묵에 밀 혼합 — 1인분 기준")),
            Map.entry("치킨", new Known("1마리", 70, 10, Confidence.HIGH, "튀김옷이 밀가루 — 프라이드 기준")),
            Map.entry("칼국수", new Known("1그릇", 80, 0, Confidence.CERTAIN, "면 전체가 밀 — 기준 앵커 메뉴")),
            Map.entry("삼겹살", new Known("1인분", 0, 0, Confidence.CERTAIN, "소금장만 사용 — 밀가루 없음")),
            Map.entry("된장찌개", new Known("1그릇", 5, 3, Confidence.HIGH, "된장에 미량 — 시판 된장 기준")),
            Map.entry("제육볶음", new Known("1인분", 15, 5, Confidence.MEDIUM, "시판 고추장 베이스로 추정")),
            Map.entry("냉면", new Known("1그릇", 40, 10, Confidence.MEDIUM, "면에 밀가루 혼합 — 비율은 가게마다")));

    private static final List<Candidate> FALLBACK_CANDIDATES = List.of(
            new Candidate("칼국수", 80, 0, Confidence.CERTAIN),
            new Candidate("수제비", 75, 10, Confidence.HIGH),
            new Candidate("우동", 70, 10, Confidence.HIGH));

    @Override
    public List<Estimate> estimateText(EstimateRequest request) {
        return request.queries().stream().map(this::estimate).toList();
    }

    private Estimate estimate(String query) {
        Known known = KNOWN.get(query.trim());
        if (known == null) {
            return new Estimate(query, false, null, null, null, null, null, null, FALLBACK_CANDIDATES);
        }
        return new Estimate(query, true, query.trim(), known.unit(), known.points(), known.pm(),
                known.confidence(), known.basis(), null);
    }
}
