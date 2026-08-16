package app.mildang.item;

import app.mildang.analysis.Analysis;
import app.mildang.analysis.AnalysisService;
import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeService;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
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

    /** 아직 확정 전이라 흥정·수정이 가능한 상태 — 스캔 메뉴당 하나만 살아 있다 */
    static final List<ItemStatus> LIVE = List.of(ItemStatus.PENDING, ItemStatus.HAGGLED);

    /** 같은 항목 재생성을 중복 제출로 보는 창 — 사람이 의도적으로 두 번 담기엔 너무 짧다 */
    private static final java.time.Duration DEDUPE_WINDOW = java.time.Duration.ofSeconds(3);

    private final ItemRepository itemRepository;
    private final ChallengeService challengeService;
    private final AnalysisService analysisService;
    private final app.mildang.scan.ScanService scanService;

    public ItemService(ItemRepository itemRepository, ChallengeService challengeService,
                       AnalysisService analysisService, app.mildang.scan.ScanService scanService) {
        this.itemRepository = itemRepository;
        this.challengeService = challengeService;
        this.analysisService = analysisService;
        this.scanService = scanService;
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

        // 스캔 메뉴 한 칸 = 살아있는 항목 하나. 이미 있으면 그걸 돌려준다 — 밀당한 항목을 두고
        // 원래값짜리 새 항목이 생겨 흥정 결과가 사라지던 문제(이슈 #1)
        if (request.scanId() != null && request.menuId() != null) {
            Item existing = findLiveScanItem(challenge, request.scanId(), request.menuId());
            if (existing != null && existing.getKind() == request.kind()) {
                return view(existing, challenge);
            }
        }

        Instant now = Instant.now();
        LocalDate logicalDate = LogicalDate.of(now);

        Item item = new Item();
        item.setId(Ids.next(Ids.Prefix.ITEM));
        item.setUserId(userId);
        item.setChallengeId(challenge.getId());
        item.setKind(request.kind());
        item.setStatus(ItemStatus.PENDING);
        item.setWeekday(request.weekday());
        item.setLogicalDate(logicalDate);
        item.setExpiresAt(request.kind() == ItemKind.MEAL ? LogicalDate.expiryOf(logicalDate) : null);
        item.setCreatedAt(now);

        if (request.analysisId() != null) {
            // (A) 3c 분석 결과로 — 만료·미해결이면 404 (§6.3)
            Analysis analysis = analysisService.requireUsable(userId, request.analysisId());
            item.setSourceType(SourceType.TEXT);
            item.setSourceRefId(analysis.getId());
            item.setOriginalName(analysis.getName());
            item.setOriginalUnit(analysis.getUnit() != null ? analysis.getUnit() : "1인분");
            item.setOriginalPoints(analysis.getPoints());
            item.setOriginalPm(analysis.getPm());
            item.setOriginalConfidence(analysis.getConfidence());
            item.setOriginalBasis(analysis.getBasis());
        } else if (request.presetId() != null) {
            // (C) 자주 먹는 것 프리셋으로
            Presets.Preset preset = Presets.byId(request.presetId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "프리셋을 찾을 수 없어요."));
            item.setSourceType(SourceType.PRESET);
            item.setSourceRefId(preset.id());
            item.setOriginalName(preset.name());
            item.setOriginalUnit(preset.unit());
            item.setOriginalPoints(preset.points());
            item.setOriginalPm(preset.pm());
            item.setOriginalConfidence(preset.confidence());
            item.setOriginalBasis(preset.basis());
        } else {
            // (B) 스캔 메뉴로 — 생성 시점 값을 original로 복사 (수정 소급 없음, §5.5)
            if (request.menuId() == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "scanId에는 menuId가 함께 필요해요.", "menuId", null);
            }
            app.mildang.scan.ScanMenu menu = scanService.requireMenu(userId, request.scanId(), request.menuId());
            item.setSourceType(SourceType.IMAGE);
            item.setSourceScanId(menu.getScanId());
            item.setSourceMenuNo(menu.getMenuNo());
            item.setOriginalName(menu.getName());
            item.setOriginalUnit("1인분");
            item.setOriginalPoints(menu.getPoints());
            item.setOriginalPm(menu.getPm());
            item.setOriginalConfidence(menu.getConfidence());
            item.setOriginalBasis(menu.getBasis() != null ? menu.getBasis() : "메뉴판 스캔 추정");
        }

        // 더블 탭·한글 IME Enter 이중 발생 방어 (이슈 #1 추가 피드백) — 같은 메뉴·같은 값의
        // 미확정 항목이 방금 만들어졌으면 새로 만들지 않고 그걸 돌려준다. 클라 가드가 빠진
        // 클라이언트도 보호된다. 사용자가 몇 초 안에 같은 메뉴를 두 번 담으려면 시트를 닫고
        // 다시 열어 또 입력해야 하므로 오탐은 사실상 없다.
        Item justCreated = findRecentDuplicate(challenge, item, now);
        if (justCreated != null) {
            return view(justCreated, challenge);
        }

        itemRepository.save(item);
        return view(item, challenge);
    }

    /** 방금(DEDUPE_WINDOW 안에) 만들어진 같은 내용의 미확정 항목 (없으면 null) */
    private Item findRecentDuplicate(Challenge challenge, Item candidate, Instant now) {
        Instant since = now.minus(DEDUPE_WINDOW);
        return itemRepository
                .findByChallengeIdAndStatusInOrderByCreatedAtDesc(challenge.getId(), LIVE)
                .stream()
                .filter(i -> i.getCreatedAt().isAfter(since))
                .filter(i -> i.getKind() == candidate.getKind())
                .filter(i -> i.getOriginalName().equals(candidate.getOriginalName()))
                .filter(i -> i.getOriginalPoints() == candidate.getOriginalPoints())
                .filter(i -> i.getWeekday() == candidate.getWeekday())
                .findFirst()
                .orElse(null);
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

        // v1.3 §6.2: summary는 쿼리 필터와 무관하게 미기록(PENDING·HAGGLED·EXPIRED) 전체를 집계 — 고정값
        List<Item> unrecorded = itemRepository
                .findByChallengeIdAndStatusInOrderByCreatedAtDesc(challenge.getId(), UNRECORDED);
        int total = unrecorded.stream().mapToInt(Item::effectivePoints).sum();
        return new ListResponse(
                items.stream().map(i -> view(i, challenge)).toList(),
                new Summary(unrecorded.size(), total, challenge.getBalance() - total));
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
                // v1.3 §6.5: 선차감은 기간과 무관하게 총 예산에서 즉시 — 귀속 구간(targetWeek) 폐지
                alreadyProcessed = false;
            }
            case PREPAID -> alreadyProcessed = true;
            default -> throw new ApiException(ErrorCode.ITEM_ALREADY_RECORDED);
        }

        return new PrepayResponse(view(item, challenge), challengeService.budgetView(challenge),
                overflowOf(item), alreadyProcessed);
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

    /** 해당 스캔 메뉴로 만들어져 아직 확정되지 않은 항목 (없으면 null) — 소유 검증은 requireMenu가 한다 */
    private Item findLiveScanItem(Challenge challenge, String scanId, String menuId) {
        app.mildang.scan.ScanMenu menu = scanService.requireMenu(challenge.getUserId(), scanId, menuId);
        return itemRepository
                .findFirstByChallengeIdAndSourceScanIdAndSourceMenuNoAndStatusInOrderByCreatedAtDesc(
                        challenge.getId(), menu.getScanId(), menu.getMenuNo(), LIVE)
                .orElse(null);
    }

    /** 다른 도메인(흥정 등)에서 항목 응답을 만들 때 사용 */
    public ItemView viewOf(Item item, Challenge challenge) {
        return view(item, challenge);
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
                new SourceView(item.getSourceType(), item.getSourceRefId(), item.getSourceScanId(),
                        item.getSourceMenuNo() == null ? null : "mnu_" + item.getSourceMenuNo()),
                new OriginalView(item.getOriginalName(), item.getOriginalUnit(), item.getOriginalPoints(),
                        item.getOriginalPm(), item.getOriginalConfidence(), item.getOriginalBasis()),
                adjusted,
                new EffectiveView(effective, balanceAfter, balanceIfOriginal),
                item.getWeekday(), item.getLogicalDate().toString(), item.getCreatedAt(),
                item.getExpiresAt(), item.getRecordedAt());
    }
}
