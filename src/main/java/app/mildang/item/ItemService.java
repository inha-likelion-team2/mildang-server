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
        item.setUserId(userId);
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

    /**
     * 기록하기 — 명세 §6.9 멱등 표.
     * PENDING/HAGGLED/EXPIRED → 차감 · RECORDED → 멱등 · PREPAID → 전이만(prepaid→spent 이동, 잔액 불변) · CANCELED → 404
     */
    @Transactional
    public RecordResponse record(String userId, String itemId) {
        Challenge challenge = challengeService.requireActive(userId);
        Item item = owned(challenge, itemId);
        int effective = item.effectivePoints();

        boolean alreadyProcessed;
        switch (item.getStatus()) {
            case PENDING, HAGGLED, EXPIRED -> {
                int before = challenge.getBalance();
                challenge.setBalance(before - effective);
                challenge.setSpent(challenge.getSpent() + effective);
                item.snapshotBalances(before, before - effective, before - item.getOriginalPoints());
                item.setStatus(ItemStatus.RECORDED);
                item.setRecordedAt(Instant.now());
                alreadyProcessed = false;
            }
            case PREPAID -> {
                // 선차감 시 이미 잔액 반영됨 — prepaid → spent 이동만 (§0.10: balance 불변)
                challenge.setPrepaid(challenge.getPrepaid() - effective);
                challenge.setSpent(challenge.getSpent() + effective);
                item.setStatus(ItemStatus.RECORDED);
                item.setRecordedAt(Instant.now());
                alreadyProcessed = true;
            }
            case RECORDED -> alreadyProcessed = true;
            default -> throw new ApiException(ErrorCode.NOT_FOUND);
        }

        return new RecordResponse(view(item, challenge), challengeService.budgetView(challenge),
                overflowOf(item), alreadyProcessed);
    }

    /**
     * 선차감 — 명세 §6.9. PENDING/HAGGLED → 차감 · PREPAID → 멱등 · 그 외 종착 상태 → 409.
     */
    @Transactional
    public PrepayResponse prepay(String userId, String itemId) {
        Challenge challenge = challengeService.requireActive(userId);
        Item item = owned(challenge, itemId);
        if (item.getKind() != ItemKind.PROMISE) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "선차감은 약속 항목에만 할 수 있어요.", "id", null);
        }

        boolean alreadyProcessed;
        switch (item.getStatus()) {
            case PENDING, HAGGLED -> {
                int effective = item.effectivePoints();
                int before = challenge.getBalance();
                challenge.setBalance(before - effective);
                challenge.setPrepaid(challenge.getPrepaid() + effective);
                item.snapshotBalances(before, before - effective, before - item.getOriginalPoints());
                item.setStatus(ItemStatus.PREPAID);
                item.setPrepaidAt(Instant.now());
                if (challenge.getPeriod() == Period.W4) {
                    item.setWeekNo(targetWeek(challenge, item.getWeekday()));
                }
                alreadyProcessed = false;
            }
            case PREPAID -> alreadyProcessed = true;
            default -> throw new ApiException(ErrorCode.ITEM_ALREADY_RECORDED);
        }

        return new PrepayResponse(view(item, challenge), challengeService.budgetView(challenge),
                item.getWeekNo(), overflowOf(item), alreadyProcessed);
    }

    /** record·prepay 공통 — 차감 결과 잔액이 음수면 overflow (명세 §6.4·§6.5, 스키마 §8.2) */
    private static Overflow overflowOf(Item item) {
        if (item.getBalanceAfter() == null || item.getBalanceAfter() >= 0) {
            return null;
        }
        int reducedBy = item.getBalanceAfter() - item.getBalanceIfOriginal();
        String note = reducedBy > 0
                ? "흥정으로 " + reducedBy + "만큼 덜 깊어졌어요."
                : "초과분은 리포트에 정직하게만 적어둘게요.";
        return new Overflow(item.getBalanceAfter(), item.getBalanceIfOriginal(), reducedBy, note);
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

    /** 삭제 — 명세 §6.9: RECORDED/PREPAID → 409 · CANCELED → 204 멱등 · 그 외 → CANCELED */
    @Transactional
    public void delete(String userId, String itemId) {
        Challenge challenge = challengeService.requireActive(userId);
        Item item = owned(challenge, itemId);
        switch (item.getStatus()) {
            case RECORDED, PREPAID ->
                    throw new ApiException(ErrorCode.ITEM_ALREADY_RECORDED, "이미 확정된 항목은 지울 수 없어요.");
            case CANCELED -> {
            }
            default -> {
                item.setStatus(ItemStatus.CANCELED);
                item.setCanceledAt(Instant.now());
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
        // 차감 완료 항목은 스냅샷을, 미차감 항목은 현재 잔액 기준 예상값을 보여준다
        int balanceAfter = item.isDeducted() ? item.getBalanceAfter() : challenge.getBalance() - effective;
        int balanceIfOriginal = item.isDeducted() ? item.getBalanceIfOriginal()
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
