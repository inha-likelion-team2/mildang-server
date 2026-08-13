package app.mildang.haggle;

import app.mildang.common.auth.CurrentUser;
import app.mildang.haggle.HaggleDtos.CloseResponse;
import app.mildang.haggle.HaggleDtos.MessageRequest;
import app.mildang.haggle.HaggleDtos.MessageResponse;
import app.mildang.haggle.HaggleDtos.OpenRequest;
import app.mildang.haggle.HaggleDtos.OpenResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/haggles")
public class HaggleController {

    private final HaggleService haggleService;

    public HaggleController(HaggleService haggleService) {
        this.haggleService = haggleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpenResponse open(@CurrentUser String userId, @Valid @RequestBody OpenRequest request) {
        return haggleService.open(userId, request);
    }

    @PostMapping("/{id}/messages")
    public MessageResponse message(@CurrentUser String userId, @PathVariable String id,
                                   @Valid @RequestBody MessageRequest request) {
        return haggleService.message(userId, id, request.text());
    }

    @PostMapping("/{id}/close")
    public CloseResponse close(@CurrentUser String userId, @PathVariable String id) {
        return haggleService.close(userId, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void abandon(@CurrentUser String userId, @PathVariable String id) {
        haggleService.abandon(userId, id);
    }
}
