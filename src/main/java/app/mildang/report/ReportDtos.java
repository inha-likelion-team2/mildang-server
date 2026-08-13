package app.mildang.report;

import app.mildang.challenge.Period;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public class ReportDtos {

    public record ChallengeView(String id, Period period, String label, Instant completedAt) {
    }

    public record Stat(String key, String label, String value, String sub) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Metric(String conditionKey, Integer thresholdPoints, Double ratio) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Finding(boolean available, String headline, Metric metric, String sampleNote, Sample sample) {
    }

    public record Sample(int answeredDays, int totalDays) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Best(String menu, String originalLabel, String adjustedLabel, int savedPoints, String when) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record HaggleHighlight(int totalSaved, Best best, double avgTurns, int longestTurns) {
    }

    public record NextChallenge(Period period, String optionKey, int suggestedBudget, String ctaLabel) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReportResponse(ChallengeView challenge, String title, List<Stat> stats, Finding finding,
                                 HaggleHighlight haggleHighlight, String disclaimer, NextChallenge nextChallenge) {
    }

    public record ShareCardRequest(List<String> mentions, String format) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ShareCardResponse(Boolean mocked, String imageUrl, int width, int height,
                                    String deepLink, String hashtag, Instant expiresAt) {
    }

    public record InviteResponse(String inviterNickname, Period period, String finding, String ctaLabel) {
    }
}
