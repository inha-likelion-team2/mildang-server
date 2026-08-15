package app.mildang.scan;

import app.mildang.common.model.Confidence;
import app.mildang.item.ItemStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public class ScanDtos {

    public record ScanResponse(String id, String place, Confidence placeConfidence, Instant scannedAt,
                               List<MenuRow> menus, Recommendation recommendation) {
    }

    public record MenuRow(String id, String name, int points, int pm, Confidence confidence,
                          String basis, boolean edited, MenuItemView item) {
    }

    /**
     * 이 메뉴로 만들어져 아직 확정 전인 항목 (없으면 null).
     * 흥정을 마치고 4b로 돌아왔을 때 행에 조정값을 그대로 보여주기 위한 것 (§5.3).
     * {@code points}는 effective — 흥정했으면 합의값, 아니면 메뉴 원래값.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MenuItemView(String id, ItemStatus status, int points, String label, boolean haggled) {
    }

    public record Recommendation(String menuId, int points, String comment) {
    }

    public record PatchMenuRequest(@NotNull @Min(0) @Max(999) Integer points) {
    }
}
