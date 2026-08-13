package app.mildang.haggle;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public class HaggleDtos {

    public record OpenRequest(@NotBlank String itemId, @NotBlank String entryPoint) {
    }

    public record TargetView(String name, String unit, int points, int pm, String place) {
    }

    public record AgreedView(String key, String label, int points) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OpenResponse(String id, String itemId, String entryPoint, int maxTurns, int turn,
                               TargetView target, AgreedView agreed, int balance, String frame,
                               String opening, List<String> chips, String status) {
    }

    public record MessageRequest(@NotBlank @Size(min = 1, max = 200) String text) {
    }

    public record ReplyView(String text, boolean hasProposal) {
    }

    public record ProposalView(String key, String label, int points, String lever, String basis) {
    }

    public record SimRow(String row, int balanceAfter, boolean overflow) {
    }

    public record SimulationView(SimRow adjusted, SimRow original) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MessageResponse(int turn, int maxTurns, int turnsLeft, ReplyView reply,
                                  ProposalView proposal, String frame, SimulationView simulation,
                                  AgreedView agreed, String closeButtonLabel, String status) {
    }

    public record CloseHaggleView(String id, String status, int turns, Instant closedAt) {
    }

    public record CloseResponse(CloseHaggleView haggle, app.mildang.item.ItemDtos.ItemView item,
                                String farewell) {
    }
}
