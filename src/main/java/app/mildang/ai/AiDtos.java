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

    // ---- POST /internal/haggle-turn ----
    public record HaggleTarget(String name, String unit, int points, int pm, String basis) {
    }

    public record HaggleBudget(int balance, int total, int mealsLeft, int daysLeft) {
    }

    public record AgreedProposal(String key, String label, int points) {
    }

    public record LastAgreement(String label, int points, int daysAgo) {
    }

    /** role: "user" / "assistant" (소문자) */
    public record HaggleHistoryItem(String role, String text, Integer proposalPoints) {
    }

    public record HaggleInput(HaggleTarget target, HaggleBudget budget, String entryPoint, String frame,
                              int turn, int maxTurns, AgreedProposal currentAgreed,
                              LastAgreement lastAgreement, List<HaggleHistoryItem> history) {
    }

    public record HaggleProposal(String key, String label, int points, String lever, String basis) {
    }

    public record HaggleOutput(String text, HaggleProposal proposal, List<String> chips) {
    }

    // ---- POST /internal/analyze-image (raw JPEG/PNG bytes) ----
    public record ImageMenuAnalysisResult(String place, Confidence placeConfidence, String cuisine,
                                          List<String> menus, int unreadableCount) {
    }

    // ---- POST /internal/scan-comment ----
    public record ScanCommentMenu(String name, int points) {
    }

    public record ScanCommentBudget(int balance, int mealsLeft) {
    }

    public record ScanCommentRequest(ScanCommentMenu target, ScanCommentMenu compareWith,
                                     ScanCommentBudget budget, String place) {
    }

    // ---- POST /internal/dashboard-tip ----
    public record TipChallenge(String period, int dayIndex, int budget, int balance) {
    }

    public record TipSignal(String type, String detail) {
    }

    public record DashboardTipRequest(TipChallenge challenge, List<TipSignal> signals,
                                      List<String> recentTipBases) {
    }

    public record DashboardTip(String text, String basis) {
    }

    // ---- POST /internal/finding ----
    public record FindingGroup(int days, int badCount) {
    }

    public record FindingStats(String conditionKey, int thresholdPoints, double ratio,
                               FindingGroup highGroup, FindingGroup lowGroup,
                               int answeredDays, int totalDays) {
    }

    public record FindingChallenge(String period, int totalSpent, int budget) {
    }

    public record FindingHaggle(int totalSaved, double avgTurns) {
    }

    public record FindingRequest(FindingStats stats, FindingChallenge challenge, FindingHaggle haggle) {
    }

    public record Finding(String headline, String sampleNote) {
    }
}
