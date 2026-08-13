package app.mildang.ai;

import app.mildang.ai.AiDtos.Estimate;
import app.mildang.ai.AiDtos.EstimateRequest;
import java.util.List;

/**
 * AI-Server 호출 경계. 구현은 HttpAiGateway(실 서버) / FakeAiGateway(mildang.ai.fake=true).
 * 검증 게이트·재시도·폴백은 호출하는 쪽(백엔드 서비스)의 책임 — AI는 stateless (명세 §15.8).
 */
public interface AiGateway {

    /** estimate_menus() — 실패(연결·5xx)는 AiUnavailableException으로 던진다 */
    List<Estimate> estimateText(EstimateRequest request);

    /** reply_haggle() */
    AiDtos.HaggleOutput haggleTurn(AiDtos.HaggleInput input);

    /** extract_menus() — 백엔드가 전처리한 JPEG/PNG bytes */
    AiDtos.ImageMenuAnalysisResult extractMenus(byte[] image);

    /** write_scan_comment() — 문자열 하나 반환 */
    String scanComment(AiDtos.ScanCommentRequest request);

    /** write_dashboard_tip() */
    AiDtos.DashboardTip dashboardTip(AiDtos.DashboardTipRequest request);

    /** write_finding() */
    AiDtos.Finding finding(AiDtos.FindingRequest request);

    class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
