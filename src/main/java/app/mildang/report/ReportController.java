package app.mildang.report;

import app.mildang.common.auth.CurrentUser;
import app.mildang.report.ReportDtos.InviteResponse;
import app.mildang.report.ReportDtos.ReportResponse;
import app.mildang.report.ReportDtos.ShareCardRequest;
import app.mildang.report.ReportDtos.ShareCardResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/challenges/{id}/report")
    public ReportResponse report(@CurrentUser String userId, @PathVariable String id) {
        return reportService.get(userId, id);
    }

    @PostMapping("/challenges/{id}/report/share-card")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareCardResponse shareCard(@CurrentUser String userId, @PathVariable String id,
                                       @RequestBody(required = false) ShareCardRequest request) {
        return reportService.shareCard(userId, id,
                request != null ? request : new ShareCardRequest(null, null));
    }

    /** 딥링크 랜딩 — 무인증 (WebConfig 제외 목록, §1) */
    @GetMapping("/invites/{code}")
    public InviteResponse invite(@PathVariable String code) {
        return reportService.invite(code);
    }
}
