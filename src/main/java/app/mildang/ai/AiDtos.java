package app.mildang.ai;

import app.mildang.common.model.Confidence;
import java.util.List;

/** AI-Server 내부 HTTP 계약 — Mildang-AI-Server `app/api/routes.py` 기준 (2026-08-13 확인) */
public class AiDtos {

    /** POST /internal/analyze-text 요청 */
    public record EstimateRequest(List<String> queries, String cuisine, String mode) {

        public static EstimateRequest freetext(String query) {
            return new EstimateRequest(List.of(query), null, "FREETEXT");
        }

        public static EstimateRequest menuboard(List<String> queries, String cuisine) {
            return new EstimateRequest(queries, cuisine, "MENUBOARD");
        }
    }

    /** POST /internal/analyze-text 응답 원소 */
    public record Estimate(String query, boolean resolved, String name, String unit,
                           Integer points, Integer pm, Confidence confidence, String basis,
                           List<Candidate> candidates) {
    }

    public record Candidate(String name, int points, int pm, Confidence confidence) {
    }
}
