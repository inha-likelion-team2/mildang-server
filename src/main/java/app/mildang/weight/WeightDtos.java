package app.mildang.weight;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class WeightDtos {

    /** kg — 사람 몸무게 범위를 벗어난 값은 오타로 본다 */
    public record PutRequest(
            @NotNull @DecimalMin("20.0") @DecimalMax("300.0") BigDecimal weightKg) {
    }

    public record WeightPoint(String date, int dayIndex, BigDecimal weightKg) {
    }

    public record WeightResponse(WeightPoint today, List<WeightPoint> series) {
    }
}
