package app.mildang.item;

import app.mildang.challenge.ChallengeDtos.BudgetView;
import app.mildang.common.model.Confidence;
import app.mildang.common.model.Weekday;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public class ItemDtos {

    public record CreateItemRequest(@NotNull ItemKind kind, String analysisId, String scanId,
                                    String menuId, String presetId, Weekday weekday) {
    }

    public record OriginalView(String name, String unit, int points, int pm,
                               Confidence confidence, String basis) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AdjustedView(String label, int points, String basis, String haggleId, Integer turns) {
    }

    public record SourceView(SourceType type, String refId) {
    }

    public record EffectiveView(int points, int balanceAfter, int balanceIfOriginal) {
    }

    public record ItemView(String id, ItemKind kind, ItemStatus status, SourceView source,
                           OriginalView original, AdjustedView adjusted, EffectiveView effective,
                           Weekday weekday, String logicalDate, Instant createdAt,
                           Instant expiresAt, Instant recordedAt) {
    }

    public record ListResponse(List<ItemView> items, Summary summary) {
    }

    public record Summary(int count, int totalPoints, int balanceAfterAll) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecordResponse(ItemView item, BudgetView budget, Overflow overflow) {
    }

    public record Overflow(int balance, int originalWouldBe, int reducedBy, String note) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PrepayResponse(ItemView item, BudgetView budget, Integer targetWeek) {
    }

    public record PresetsResponse(List<PresetView> presets, String source) {
    }

    public record PresetView(String id, String name, String unit, int points, int pm) {
    }
}
