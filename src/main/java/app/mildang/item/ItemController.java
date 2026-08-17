package app.mildang.item;

import app.mildang.common.auth.CurrentUser;
import app.mildang.item.ItemDtos.CreateItemRequest;
import app.mildang.item.ItemDtos.ItemView;
import app.mildang.item.ItemDtos.ListResponse;
import app.mildang.item.ItemDtos.PrepayResponse;
import app.mildang.item.ItemDtos.PresetsResponse;
import app.mildang.item.ItemDtos.RecordResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @GetMapping("/presets")
    public PresetsResponse presets(@CurrentUser String userId) {
        return itemService.presets(userId);
    }

    @GetMapping("/items")
    public ListResponse list(@CurrentUser String userId,
                             @RequestParam(required = false) ItemKind kind,
                             @RequestParam(required = false) String status,
                             @RequestParam(defaultValue = "20") int limit,
                             @RequestParam(required = false) String date) {
        return itemService.list(userId, kind, status, limit, date);
    }

    /** 캘린더 — 그 달에 기록이 있는 날 (화면 «기록 보기»의 날짜 선택) */
    @GetMapping("/items/dates")
    public ItemDtos.RecordedDaysResponse recordedDays(@CurrentUser String userId,
                                                      @RequestParam(required = false) String month) {
        return itemService.recordedDays(userId, month);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView create(@CurrentUser String userId, @Valid @RequestBody CreateItemRequest request) {
        return itemService.create(userId, request);
    }

    @PostMapping("/items/{id}/record")
    public RecordResponse record(@CurrentUser String userId, @PathVariable String id) {
        return itemService.record(userId, id);
    }

    @PostMapping("/items/{id}/prepay")
    public PrepayResponse prepay(@CurrentUser String userId, @PathVariable String id) {
        return itemService.prepay(userId, id);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentUser String userId, @PathVariable String id) {
        itemService.delete(userId, id);
    }
}
