package app.mildang.item;

import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeService;
import app.mildang.challenge.Period;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import app.mildang.common.model.Weekday;
import app.mildang.common.time.LogicalDate;
import app.mildang.item.ItemDtos.AdjustedView;
import app.mildang.item.ItemDtos.CreateItemRequest;
import app.mildang.item.ItemDtos.EffectiveView;
import app.mildang.item.ItemDtos.ItemView;
import app.mildang.item.ItemDtos.ListResponse;
import app.mildang.item.ItemDtos.Overflow;
import app.mildang.item.ItemDtos.OriginalView;
import app.mildang.item.ItemDtos.PrepayResponse;
import app.mildang.item.ItemDtos.PresetView;
import app.mildang.item.ItemDtos.PresetsResponse;
import app.mildang.item.ItemDtos.RecordResponse;
import app.mildang.item.ItemDtos.SourceView;
import app.mildang.item.ItemDtos.Summary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {

    private static final List<ItemStatus> UNRECORDED =
            List.of(ItemStatus.PENDING, ItemStatus.HAGGLED, ItemStatus.EXPIRED);

    private final ItemRepository itemRepository;
    private final ChallengeService challengeService;

    public ItemService(ItemRepository itemRepository, ChallengeService challengeService) {
        this.itemRepository = itemRepository;
        this.challengeService = challengeService;
    }

    public PresetsResponse presets() {
        // TODO 이력 4주 집계(HISTORY)로 교체 — 지금은 기본 4종 (명세 §6.7)
        return new PresetsResponse(
                Presets.DEFAULTS.stream()
                        .map(p -> new PresetView(p.id(), p.name(), p.unit(), p.points(), p.pm()))
                        .toList(),
                "DEFAULT");
    }

    @Transactional
    public ItemView create(String userId, CreateItemRequest request) {
        Challenge challenge = challengeService.requireActive(userId);

        long sources = Stream.of(request.analysisId(), request.scanId(), request.presetId())
                .filter(Objects::nonNull).count();
        if (sources != 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "analysisId / scanId+menuId / presetId 중 정확히 하나만 보내주세요.", "source", null);
        }
        if (request.kind() == ItemKind.PROMISE && request.weekday() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "약속에는 요일이 필요해요.", "weekday", null);
        }
        if (request.presetId() == null) {
            // AI 분석·스캔 연동 전 — analysisId/scanId 경로는 다음 단계에서 열린다
            throw new ApiException(ErrorCode.NOT_FOUND, "분석 결과를 찾을 수 없어요. 다시 분석해 주세요.");
        }

        Presets.Preset preset = Presets.byId(request.presetId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "프리셋을 찾을 수 없어요."));

        Instant now = Instant.now();
        LocalDate logicalDate = LogicalDate.of(now);

        Item item = new Item();
        item.setId(Ids.next(Ids.Prefix.ITEM));
        item.setChallengeId(challenge.getId());
        item.setKind(request.kind());
        item.setStatus(ItemStatus.PENDING);
        item.setSourceType(SourceType.PRESET);
        item.setSourceRefId(preset.id());
        item.setOriginalName(preset.name());
        item.setOriginalUnit(preset.unit());
        item.setOriginalPoints(preset.points());
        item.setOriginalPm(preset.pm());
        item.setOriginalConfidence(preset.confidence());
        item.setOriginalBasis(preset.basis());
        item.setWeekday(request.weekday());
        item.setLogicalDate(logicalDate);
        item.setExpiresAt(request.kind() == ItemKind.MEAL ? LogicalDate.expiryOf(logicalDate) : null);
        item.setCreatedAt(now);
        itemRepository.save(item);

        return view(item, challenge);
    }

    @Transactional(readOnly = true)
    public ListResponse list(String userId, ItemKind kind, String statusCsv, int limit) {
        Challenge challenge = challengeService.requireActive(userId);
        List<ItemStatus> statuses = parseStatuses(statusCsv);
        List<Item> items = (kind == null
                ? itemRepository.findByChallengeIdAndStatusInOrderByCreatedAtDesc(challenge.getId(), statuses)
                : itemRepository.findByChallengeIdAndKindAndStatusInOrderByCreatedAtDesc(
                        challenge.getId(), kind, statuses))
                .stream().limit(Math.min(limit, 50)).toList();

        int total = items.stream()
                .filter(i -> UNRECORDED.contains(i.getStatus()))
                .mapToInt(Item::effectivePoints).sum();
        return new ListResponse(
                items.stream().map(i -> view(i, challenge)).toList(),
                new Summary(items.size(), total, challenge.getBalance() - total));
    }

    private static List<ItemStatus> parseStatuses(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of(ItemStatus.PENDING, ItemStatus.HAGGLED);
        }
        try {
            return Arrays.stream(csv.split(",")).map(String::trim).map(ItemStatus::valueOf).toList();
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "status 값이 올바르지 않아요.", "status", null);
        }
    }

    /** 기록하기 — 잔액이 변하는 두 지점 중 하나 (§11 불변). 초과는 항상 허용. */
    @Transactional
    public RecordResponse record(String userId, String itemId) {
        Challenge challenge = challengeService.requireActive(userId);
        Item item = owned(challenge, itemId);
        requireRecordable(item);

        int before = challenge.getBalance();
        int effective = item.effectivePoints();
        challenge.setBalance(before - effective);
        challenge.setSpentTotal(challenge.getSpentTotal() + effective);
        item.setStatus(ItemStatus.RECORDED);
        item.setRecordedAt(Instant.now());

        Overflow overflow = null;
        if (challenge.getBalance() < 0) {
            int originalWouldBe = before - item.getOriginalPoints();
            int reducedBy = item.getOriginalPoints() - effective;
            String note = reducedBy > 0
                    ? "흥정으로 " + reducedBy + "만큼 덜 깊어졌어요."
                    : "초과분은 리포트에 정직하게만 적어둘게요.";
            overflow = new Overflow(challenge.getBalance(), originalWouldBe, reducedBy, note);
        }
        return new RecordResponse(view(item, challenge), challengeService.budgetView(challenge), overflow);
    }

    /** 선차감 — 멱등 (2026-08-11 결정): 이미 PREPAID면 추가 차감 없이 현재 상태 반환. */
    @Transactional
    public PrepayResponse prepay(String userId, String itemId) {
        Challenge challenge = challengeService.requireActive(userId);
        Item item = owned(challenge, itemId);
        if (item.getKind() != ItemKind.PROMISE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "선차감은 약속 항목에만 할 수 있어요.", "id", null);
        }
        if (item.getStatus() != ItemStatus.PREPAID) {
            requireRecordable(item);
            int effective = item.effectivePoints();
            challenge.setBalance(challenge.getBalance() - effective);
            challenge.setPrepaidTotal(challenge.getPrepaidTotal() + effective);
            item.setStatus(ItemStatus.PREPAID);
            if (challenge.getPeriod() == Period.W4) {
                item.setTargetWeek(targetWeek(challenge, item.getWeekday()));
            }
        }
        return new PrepayResponse(view(item, challenge), challengeService.budgetView(challenge),
                item.getTargetWeek());
    }

    /** 약속 요일이 속한 주차 (W4 전용, 명세 §6.5) */
    private static int targetWeek(Challenge challenge, Weekday weekday) {
        LocalDate today = LogicalDate.of(Instant.now());
        LocalDate target = today;
        while (target.getDayOfWeek() != weekday.dayOfWeek()) {
            target = target.plusDays(1);
        }
        LocalDate start = LogicalDate.of(challenge.getStartedAt());
        int week = (int) (ChronoUnit.DAYS.between(start, target) / 7) + 1;
        return Math.max(1, Math.min(4, week));
    }

    @Transactional
    public void delete(String userId, String itemId) {
        Challenge challenge = challengeService.requireActive(userId);
        Item item = owned(challenge, itemId);
        if (item.getStatus() == ItemStatus.RECORDED || item.getStatus() == ItemStatus.PREPAID) {
            throw new ApiException(ErrorCode.ITEM_ALREADY_RECORDED, "이미 확정된 항목은 지울 수 없어요.");
        }
        item.setStatus(ItemStatus.CANCELED);
    }

    private static void requireRecordable(Item item) {
        switch (item.getStatus()) {
            case RECORDED, PREPAID -> throw new ApiException(ErrorCode.ITEM_ALREADY_RECORDED);
            case CANCELED -> throw new ApiException(ErrorCode.NOT_FOUND);
            case PENDING, HAGGLED, EXPIRED -> {
            }
        }
    }

    private Item owned(Challenge challenge, String itemId) {
        return itemRepository.findById(itemId)
                .filter(i -> i.getChallengeId().equals(challenge.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private ItemView view(Item item, Challenge challenge) {
        int effective = item.effectivePoints();
        boolean settled = item.getStatus() == ItemStatus.RECORDED || item.getStatus() == ItemStatus.PREPAID;
        int balanceAfter = settled ? challenge.getBalance() : challenge.getBalance() - effective;
        int balanceIfOriginal = settled
                ? challenge.getBalance() - (item.getOriginalPoints() - effective)
                : challenge.getBalance() - item.getOriginalPoints();

        AdjustedView adjusted = item.isHaggled()
                ? new AdjustedView(item.getAdjustedLabel(), item.getAdjustedPoints(), item.getAdjustedBasis(),
                        item.getAdjustedHaggleId(), item.getAdjustedTurns())
                : null;

        return new ItemView(item.getId(), item.getKind(), item.getStatus(),
                new SourceView(item.getSourceType(), item.getSourceRefId()),
                new OriginalView(item.getOriginalName(), item.getOriginalUnit(), item.getOriginalPoints(),
                        item.getOriginalPm(), item.getOriginalConfidence(), item.getOriginalBasis()),
                adjusted,
                new EffectiveView(effective, balanceAfter, balanceIfOriginal),
                item.getWeekday(), item.getLogicalDate().toString(), item.getCreatedAt(),
                item.getExpiresAt(), item.getRecordedAt());
    }
}
