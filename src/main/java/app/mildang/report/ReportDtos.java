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
    /**
     * 확정 와이어프레임 231:1237의 공유 카드 한 장 — 화면이 그대로 그릴 수 있게 문구까지 만들어 보낸다.
     * 「2주 챌린지 완주 🎉 / 이번 주 밀당 성공 ! / 밀가루 예산의 70%만 사용했어요」
     */
    public record Completion(String periodLabel, String headline, int usedPercent,
                             int totalBudget, int spent, int leftover, String summaryLine,
                             List<BodyChange> bodyChanges) {
    }

    /**
     * 「내 몸의 변화」 4칸. value가 null이면 아직 재료가 없는 칸이다 —
     * 화면은 그 칸을 «기록이 모자라요»로 비워두면 된다.
     */
    public record BodyChange(String key, String label, String value, String note) {
    }

    public record ReportResponse(ChallengeView challenge, String title, List<Stat> stats, Finding finding,
                                 HaggleHighlight haggleHighlight, String disclaimer,
                                 NextChallenge nextChallenge, Completion completion) {
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
