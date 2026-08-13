package app.mildang.ai;

import app.mildang.ai.AiDtos.Candidate;
import app.mildang.ai.AiDtos.Estimate;
import app.mildang.ai.AiDtos.EstimateRequest;
import app.mildang.common.model.Confidence;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI-Server 없이 개발·테스트용 결정적 응답 (mildang.ai.fake=true).
 * 값은 명세 §15.2.4 참조 스케일과 프리셋 기준. 모르는 메뉴는 후보 3개로 unresolved.
 */
@Component
@ConditionalOnProperty(name = "mildang.ai.fake", havingValue = "true")
public class FakeAiGateway implements AiGateway {

    private record Known(String unit, int points, int pm, Confidence confidence, String basis) {
    }

    private static final Map<String, Known> KNOWN = Map.ofEntries(
            Map.entry("라면", new Known("1봉지", 80, 10, Confidence.CERTAIN, "면 전체가 밀 — 봉지라면 1인분 기준")),
            Map.entry("김밥", new Known("1줄", 20, 5, Confidence.HIGH, "단무지·어묵에 소량")),
            Map.entry("빵", new Known("1개", 45, 10, Confidence.HIGH, "밀가루 반죽 — 기본 1개 기준")),
            Map.entry("떡볶이", new Known("1인분", 55, 10, Confidence.HIGH, "떡·어묵에 밀 혼합 — 1인분 기준")),
            Map.entry("치킨", new Known("1마리", 70, 10, Confidence.HIGH, "튀김옷이 밀가루 — 프라이드 기준")),
            Map.entry("칼국수", new Known("1그릇", 80, 0, Confidence.CERTAIN, "면 전체가 밀 — 기준 앵커 메뉴")),
            Map.entry("삼겹살", new Known("1인분", 0, 0, Confidence.CERTAIN, "소금장만 사용 — 밀가루 없음")),
            Map.entry("된장찌개", new Known("1그릇", 5, 3, Confidence.HIGH, "된장에 미량 — 시판 된장 기준")),
            Map.entry("제육볶음", new Known("1인분", 15, 5, Confidence.MEDIUM, "시판 고추장 베이스로 추정")),
            Map.entry("냉면", new Known("1그릇", 40, 10, Confidence.MEDIUM, "면에 밀가루 혼합 — 비율은 가게마다")));

    private static final List<Candidate> FALLBACK_CANDIDATES = List.of(
            new Candidate("칼국수", 80, 0, Confidence.CERTAIN),
            new Candidate("수제비", 75, 10, Confidence.HIGH),
            new Candidate("우동", 70, 10, Confidence.HIGH));

    @Override
    public List<Estimate> estimateText(EstimateRequest request) {
        return request.queries().stream().map(this::estimate).toList();
    }

    private Estimate estimate(String query) {
        Known known = KNOWN.get(query.trim());
        if (known == null) {
            return new Estimate(query, false, null, null, null, null, null, null, FALLBACK_CANDIDATES);
        }
        return new Estimate(query, true, query.trim(), known.unit(), known.points(), known.pm(),
                known.confidence(), known.basis(), null);
    }

    /** 결정적 흥정 — 칩 문구·숫자·비율 키워드로 제안 (양·구성만, 명세 §15.4) */
    @Override
    public AiDtos.HaggleOutput haggleTurn(AiDtos.HaggleInput input) {
        int target = input.target().points();
        int balance = input.budget().balance();

        if (input.turn() == 0) {
            String last = input.lastAgreement() != null
                    ? " 지난번엔 " + input.lastAgreement().points() + "으로 합의하셨죠." : "";
            String text = input.target().name() + " " + input.target().unit() + " " + target + "이요. 잔액 "
                    + balance + "니까 그대로면 " + (balance - target) + "입니다." + last;
            return new AiDtos.HaggleOutput(trim90(text), null, null);
        }

        String user = lastUserText(input);
        AiDtos.HaggleProposal proposal;
        if (user.contains("그대로")) {
            proposal = proposal("asis", input.target().unit() + " 그대로", target, "원래 구성 그대로");
        } else if (user.matches(".*\\d+.*")) {
            int asked = Integer.parseInt(user.replaceAll("\\D+", ""));
            proposal = proposal("exact", "합의 " + Math.min(asked, target), Math.min(asked, target), "말씀하신 값으로");
        } else if (user.contains("반")) {
            proposal = proposal("half", "절반", Math.round(target / 2f), "양 절반");
        } else if (user.contains("깎")) {
            proposal = proposal("third", "1/3", Math.round(target / 3f), "양 1/3");
        } else if (user.contains("적어")) {
            proposal = proposal("three", "3/4", Math.round(target * 3 / 4f), "양 3/4");
        } else {
            proposal = proposal("two", "2/3", Math.round(target * 2 / 3f), "양 2/3");
        }
        String text = proposal.label() + "이면 " + proposal.points() + "입니다. 여기서 맞춰볼까요?";
        if (input.turn() >= input.maxTurns()) {
            text = trim90(text + " 이제 정리할 시간이에요.");
        }
        return new AiDtos.HaggleOutput(trim90(text), proposal, null);
    }

    private static AiDtos.HaggleProposal proposal(String key, String label, int points, String basis) {
        return new AiDtos.HaggleProposal(key, label, points, "AMOUNT", basis);
    }

    private static String lastUserText(AiDtos.HaggleInput input) {
        for (int i = input.history().size() - 1; i >= 0; i--) {
            if ("user".equals(input.history().get(i).role())) {
                return input.history().get(i).text();
            }
        }
        return "";
    }

    private static String trim90(String text) {
        return text.length() <= 90 ? text : text.substring(0, 90);
    }

    /** 결정적 스캔 — 명세 §15.2.4 참조 스케일 5메뉴 */
    @Override
    public AiDtos.ImageMenuAnalysisResult extractMenus(byte[] image) {
        return new AiDtos.ImageMenuAnalysisResult("김밥천국 성수점", Confidence.HIGH, "KOREAN_BUNSIK",
                List.of("칼국수", "냉면", "제육볶음", "된장찌개", "삼겹살"), 0);
    }

    @Override
    public String scanComment(AiDtos.ScanCommentRequest request) {
        return trim90("\"" + request.compareWith().name() + "(" + request.compareWith().points()
                + ")을 고르면 내일이 빠듯해요. " + request.target().points() + "이면 남는 장사죠.\"");
    }

    @Override
    public AiDtos.DashboardTip dashboardTip(AiDtos.DashboardTipRequest request) {
        if (request.signals().isEmpty()) {
            return new AiDtos.DashboardTip("오늘 쓸 수 있는 예산부터 확인해보세요. 기록이 쌓이면 더 짚어드릴게요.", "GENERIC");
        }
        AiDtos.TipSignal signal = request.signals().get(0);
        return new AiDtos.DashboardTip(trim90(signal.detail() + " — 오늘도 그 페이스면 충분해요."), signal.type());
    }

    @Override
    public AiDtos.Finding finding(AiDtos.FindingRequest request) {
        String label = switch (request.stats().conditionKey()) {
            case "SKIN" -> "피부 트러블";
            case "DROWSY" -> "낮 졸림";
            default -> "더부룩함";
        };
        return new AiDtos.Finding(
                "밀가루 " + request.stats().thresholdPoints() + "+ 섭취한 다음날, " + label + " 보고율 "
                        + request.stats().ratio() + "배",
                "응답 " + request.stats().answeredDays() + "/" + request.stats().totalDays()
                        + "일 · 표본이 작아 \"경향\"으로 읽어주세요");
    }
}
