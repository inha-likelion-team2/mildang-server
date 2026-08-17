package app.mildang.analysis;

import app.mildang.common.model.Confidence;
import app.mildang.item.ItemKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public class AnalysisDtos {

    public record AnalyzeTextRequest(
            @NotBlank @Size(min = 1, max = 40) String query,
            @NotNull @Valid Context context) {
    }

    /** v1.3: balance·mealsLeft는 클라이언트가 보내지 않는다 — 서버가 challengeId로 조회 (§5.1) */
    public record Context(@NotBlank String challengeId, @NotNull ItemKind kind) {
    }

    public record AnalyzeTextResponse(String id, boolean resolved, MenuView menu,
                                      List<CandidateView> candidates, Instant expiresAt) {
    }

    public record MenuView(String name, String unit, int points, int pm,
                           Confidence confidence, String basis) {
    }

    public record CandidateView(String name, int points, int pm, Confidence confidence) {
    }

    public record RecentResponse(List<RecentEntry> recent) {
    }

    /** 화면 칩이 「라면 80」처럼 값까지 보여준다 — 값은 항상 original 기준 */
    public record RecentEntry(String name, String unit, Integer points, Integer pm) {
    }
}
