package app.mildang.item;

import app.mildang.challenge.ChallengeDtos.BudgetView;
import app.mildang.common.model.Confidence;
import app.mildang.common.model.Weekday;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public class ItemDtos {

    /**
     * 약속(PROMISE)은 화면이 「언제 예요? · 날짜 입력」이라 <b>날짜</b>로 받는다(`promiseDate`).
     * 예전 방식인 `weekday`도 계속 받는다 — 둘 중 하나만 주면 된다.
     */
    public record CreateItemRequest(@NotNull ItemKind kind, String analysisId, String scanId,
                                    String menuId, String presetId, Weekday weekday,
                                    String promiseDate) {
    }

    public record OriginalView(String name, String unit, int points, int pm,
                               Confidence confidence, String basis) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdjustedView(String label, int points, String basis, String haggleId, Integer turns) {
    }

    /** IMAGE 항목은 refId가 없다 — 스캔 좌표(scanId+menuId)로 4b 행과 이어진다 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SourceView(SourceType type, String refId, String scanId, String menuId) {
    }

    public record EffectiveView(int points, int balanceAfter, int balanceIfOriginal) {
    }

    public record ItemView(String id, ItemKind kind, ItemStatus status, SourceView source,
                           OriginalView original, AdjustedView adjusted, EffectiveView effective,
                           Weekday weekday, String logicalDate, Instant createdAt,
                           Instant expiresAt, Instant recordedAt) {
    }

    /**
     * date 필터를 준 조회에만 day·weights·progress가 채워진다. summary는 기존 의미 그대로.
     * 화면 「기록 보기」가 목록·체중 그래프·진행률을 한 화면에 같이 그려서 한 번에 실어 보낸다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ListResponse(List<ItemView> items, Summary summary, DayView day,
                               List<app.mildang.weight.WeightDtos.WeightPoint> weights,
                               app.mildang.challenge.ChallengeDtos.ProgressView progress) {
    }

    /** 화면 «기록 보기»의 「오늘 2026.08.16 · 총 5건」 */
    public record DayView(String date, int count, int totalPoints) {
    }

    /** 캘린더에 표시할 «기록이 있는 날» 목록 */
    public record RecordedDaysResponse(String month, List<DayView> days) {
    }

    public record Summary(int count, int totalPoints, int balanceAfterAll) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecordResponse(ItemView item, BudgetView budget, Overflow overflow,
                                 boolean alreadyProcessed) {
    }

    public record Overflow(int balance, int originalWouldBe, int reducedBy, String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrepayResponse(ItemView item, BudgetView budget,
                                 Overflow overflow, boolean alreadyProcessed) {
    }

    public record PresetsResponse(List<PresetView> presets, String source) {
    }

    public record PresetView(String id, String name, String unit, int points, int pm) {
    }
}
