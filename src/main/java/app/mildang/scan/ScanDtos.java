package app.mildang.scan;

import app.mildang.common.model.Confidence;
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
                          String basis, boolean edited) {
    }

    public record Recommendation(String menuId, int points, String comment) {
    }

    public record PatchMenuRequest(@NotNull @Min(0) @Max(999) Integer points) {
    }
}
