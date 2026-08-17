package app.mildang.checkin;

import app.mildang.checkin.CheckinDtos.PutRequest;
import app.mildang.checkin.CheckinDtos.PutResponse;
import app.mildang.checkin.CheckinDtos.TodayResponse;
import app.mildang.common.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/checkins")
public class CheckinController {

    private final CheckinService checkinService;

    public CheckinController(CheckinService checkinService) {
        this.checkinService = checkinService;
    }

    @GetMapping("/today")
    public TodayResponse today(@CurrentUser String userId) {
        return checkinService.today(userId);
    }

    @PutMapping("/today")
    public PutResponse put(@CurrentUser String userId, @Valid @RequestBody PutRequest request) {
        return checkinService.put(userId, request.answers(), request.weightKg());
    }
}
