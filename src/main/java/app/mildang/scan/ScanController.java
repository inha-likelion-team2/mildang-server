package app.mildang.scan;

import app.mildang.common.auth.CurrentUser;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.scan.ScanDtos.MenuRow;
import app.mildang.scan.ScanDtos.PatchMenuRequest;
import app.mildang.scan.ScanDtos.ScanResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ScanResponse create(@CurrentUser String userId,
                               @RequestPart("image") MultipartFile image,
                               @RequestParam("challengeId") String challengeId) {
        try {
            return scanService.create(userId, challengeId, image.getBytes());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미지를 읽지 못했어요.", "image", null);
        }
    }

    @GetMapping("/{id}")
    public ScanResponse get(@CurrentUser String userId, @PathVariable String id) {
        return scanService.get(userId, id);
    }

    @PatchMapping("/{id}/menus/{menuId}")
    public MenuRow patchMenu(@CurrentUser String userId, @PathVariable String id,
                             @PathVariable String menuId, @Valid @RequestBody PatchMenuRequest request) {
        return scanService.patchMenu(userId, id, menuId, request.points());
    }
}
