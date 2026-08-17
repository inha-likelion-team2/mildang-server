package app.mildang.scan;

import app.mildang.ai.AiDtos;
import app.mildang.ai.AiDtos.Estimate;
import app.mildang.ai.AiDtos.EstimateRequest;
import app.mildang.ai.AiGates;
import app.mildang.ai.AiGateway;
import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeService;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import app.mildang.common.model.Confidence;
import app.mildang.common.util.Hashes;
import app.mildang.item.Item;
import app.mildang.item.ItemRepository;
import app.mildang.item.ItemStatus;
import app.mildang.scan.ScanDtos.MenuItemView;
import app.mildang.scan.ScanDtos.MenuRow;
import app.mildang.scan.ScanDtos.Recommendation;
import app.mildang.scan.ScanDtos.ScanResponse;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScanService {

    private static final Logger log = LoggerFactory.getLogger(ScanService.class);
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_EDGE = 4096;
    private static final List<ItemStatus> LIVE = List.of(ItemStatus.PENDING, ItemStatus.HAGGLED);

    private final ScanRepository scanRepository;
    private final ScanMenuRepository scanMenuRepository;
    private final ChallengeService challengeService;
    private final AiGateway aiGateway;
    private final ItemRepository itemRepository;

    public ScanService(ScanRepository scanRepository, ScanMenuRepository scanMenuRepository,
                       ChallengeService challengeService, AiGateway aiGateway,
                       ItemRepository itemRepository) {
        this.scanRepository = scanRepository;
        this.scanMenuRepository = scanMenuRepository;
        this.challengeService = challengeService;
        this.aiGateway = aiGateway;
        this.itemRepository = itemRepository;
    }

    /** 처리 순서(§15.3.1): 전처리 → extract → estimate(batch) → 정렬·추천 → comment → 저장 */
    @Transactional
    public ScanResponse create(String userId, String challengeId, byte[] imageBytes) {
        Challenge challenge = challengeService.requireActive(userId);
        if (!challenge.getId().equals(challengeId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "진행 중인 챌린지가 아니에요.");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미지를 올려주세요.", "image", null);
        }
        if (imageBytes.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] processed = preprocess(imageBytes);

        // ① 메뉴 추출 — 재시도 1회 → 422/503
        AiDtos.ImageMenuAnalysisResult extract = extractWithRetry(processed);
        if (extract.menus().isEmpty()) {
            throw new ApiException(ErrorCode.ANALYSIS_FAILED, "메뉴를 읽지 못했어요 — 다시 찍어주세요.");
        }
        List<String> menuNames = extract.menus().stream().limit(40).toList();

        // ② 비용 책정 — 배치 1회 (§15.3.3)
        Map<String, Estimate> estimates = estimateBatch(menuNames, extract.cuisine());

        int dayIndex = challengeService.dayIndex(challenge, Instant.now());
        // 남은 끼수는 대시보드 「앞으로 N끼」와 같은 값이어야 한다 — 계산은 ChallengeService 한 곳에
        int mealsLeft = challengeService.mealsLeft(challenge, Instant.now());

        Instant now = Instant.now();
        Scan scan = new Scan();
        scan.setId(Ids.next(Ids.Prefix.SCAN));
        scan.setUserId(userId);
        scan.setChallengeId(challenge.getId());
        scan.setImageHash(Hashes.sha256(java.util.Base64.getEncoder().encodeToString(processed)));
        scan.setPlace(extract.place());
        scan.setPlaceConfidence(extract.placeConfidence());
        scan.setCuisine(extract.cuisine());
        scan.setUnreadableCount(extract.unreadableCount());
        scan.setBalanceAtScan(challenge.getBalance());
        scan.setMealsLeftAtScan(mealsLeft);
        scan.setScannedAt(now);

        // menu_no는 추출 순서(안정 키), sort_order는 points 오름차순 (스키마 §7.3)
        List<ScanMenu> menus = new ArrayList<>();
        for (int i = 0; i < menuNames.size(); i++) {
            Estimate estimate = estimates.get(menuNames.get(i));
            if (estimate == null) {
                continue;
            }
            ScanMenu menu = new ScanMenu();
            menu.setScanId(scan.getId());
            menu.setMenuNo(i + 1);
            menu.setName(estimate.name());
            menu.setPoints(estimate.points());
            menu.setPm(estimate.pm());
            menu.setConfidence(estimate.confidence());
            menu.setBasis(estimate.basis());
            menu.setAiPoints(estimate.points());
            menus.add(menu);
        }
        if (menus.isEmpty()) {
            throw new ApiException(ErrorCode.ANALYSIS_FAILED, "메뉴 가격을 매기지 못했어요 — 다시 찍어주세요.");
        }
        menus.sort(Comparator.comparingInt(ScanMenu::getPoints));
        for (int i = 0; i < menus.size(); i++) {
            menus.get(i).setSortOrder(i + 1);
        }

        // 추천: 잔액 ÷ 남은 끼수 이하 중 가장 비싼 것 (§15.3.4 — 백엔드 선정). 없으면 최저가
        int cap = Math.max(0, challenge.getBalance()) / mealsLeft;
        ScanMenu recommended = menus.stream()
                .filter(m -> m.getPoints() <= cap)
                .max(Comparator.comparingInt(ScanMenu::getPoints))
                .orElse(menus.get(0));
        scan.setRecommendedMenuNo(recommended.getMenuNo());

        // ③ 추천 코멘트 — 비교 대상: 상한 초과 중 최저가, 없으면 추천 아닌 최고가
        ScanMenu compare = menus.stream()
                .filter(m -> m.getMenuNo() != recommended.getMenuNo() && m.getPoints() > cap)
                .min(Comparator.comparingInt(ScanMenu::getPoints))
                .orElseGet(() -> menus.stream()
                        .filter(m -> m.getMenuNo() != recommended.getMenuNo())
                        .max(Comparator.comparingInt(ScanMenu::getPoints))
                        .orElse(null));
        if (compare != null) {
            scan.setRecommendationComment(commentWithRetry(recommended, compare, challenge, mealsLeft, extract.place()));
        }

        scanRepository.save(scan);
        scanMenuRepository.saveAll(menus);
        return response(scan, menus);
    }

    @Transactional(readOnly = true)
    public ScanResponse get(String userId, String scanId) {
        Scan scan = owned(userId, scanId);
        return response(scan, scanMenuRepository.findByScanIdOrderBySortOrderAsc(scanId));
    }

    /** 가격 탭 수정 — pm=0·CERTAIN·edited 고정, 실측 수집 (§5.5-6). 기존 항목엔 소급 안 됨 */
    @Transactional
    public MenuRow patchMenu(String userId, String scanId, String menuId, int points) {
        Scan scan = owned(userId, scanId);
        ScanMenu menu = scanMenuRepository.findById(new ScanMenuId(scan.getId(), parseMenuNo(menuId)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        menu.setPoints(points);
        menu.setPm(0);
        menu.setConfidence(Confidence.CERTAIN);
        menu.setBasis("직접 입력한 값");
        menu.setEdited(true);
        menu.setComment(null); // 가격이 바뀌면 코멘트가 낡는다 — 다음 탭에서 다시 만든다
        // 실측 수집 — demo는 로그만 (§14.1)
        log.info("measured-price place={} menu={} userPoints={} aiPoints={}",
                scan.getPlace(), menu.getName(), points, menu.getAiPoints());
        return row(menu);
    }

    /**
     * 하단 목록에서 메뉴를 탭했을 때 상단 메모를 채운다 (화면 4b).
     * 코멘트는 메뉴마다 다르다 — 추천 메뉴 것을 그대로 쓰면 "냉면을 고르면 안 좋다"는 말이
     * 냉면을 골랐을 때도 그대로 남아 앞뒤가 안 맞는다.
     * 한 번 만든 코멘트는 저장해서 다시 탭할 때 AI를 부르지 않는다.
     */
    @Transactional
    public ScanDtos.MenuCommentResponse menuComment(String userId, String scanId, String menuId) {
        Challenge challenge = challengeService.requireActive(userId);
        Scan scan = owned(userId, scanId);
        List<ScanMenu> menus = scanMenuRepository.findByScanIdOrderBySortOrderAsc(scanId);
        ScanMenu target = menus.stream()
                .filter(m -> m.getMenuNo() == parseMenuNo(menuId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "스캔 결과를 찾을 수 없어요."));

        if (target.getComment() == null) {
            ScanMenu compare = compareFor(target, menus);
            if (compare != null) {
                int mealsLeft = Math.max(1, challenge.getTotalDays() - challengeService.dayIndex(challenge, Instant.now()));
                target.setComment(commentWithRetry(target, compare, challenge, mealsLeft, scan.getPlace()));
            }
        }
        return new ScanDtos.MenuCommentResponse("mnu_" + target.getMenuNo(), target.getName(),
                target.getPoints(), target.getBasis(), target.getComment(),
                challenge.getBalance() - target.getPoints());
    }

    /** 비교 대상 — 탭한 메뉴보다 비싼 것 중 최저가, 없으면 그보다 싼 것 중 최고가 */
    private static ScanMenu compareFor(ScanMenu target, List<ScanMenu> menus) {
        return menus.stream()
                .filter(m -> m.getMenuNo() != target.getMenuNo() && m.getPoints() > target.getPoints())
                .min(Comparator.comparingInt(ScanMenu::getPoints))
                .orElseGet(() -> menus.stream()
                        .filter(m -> m.getMenuNo() != target.getMenuNo())
                        .max(Comparator.comparingInt(ScanMenu::getPoints))
                        .orElse(null));
    }

    /** 항목 생성용 (§6.3 B) — 소유 검증 포함 */
    @Transactional(readOnly = true)
    public ScanMenu requireMenu(String userId, String scanId, String menuId) {
        Scan scan = owned(userId, scanId);
        return scanMenuRepository.findById(new ScanMenuId(scan.getId(), parseMenuNo(menuId)))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "스캔 결과를 찾을 수 없어요."));
    }

    // ---- 내부 ----

    private static int parseMenuNo(String menuId) {
        if (menuId == null || !menuId.startsWith("mnu_")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "menuId 형식이 올바르지 않아요.", "menuId", null);
        }
        try {
            return Integer.parseInt(menuId.substring(4));
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "menuId 형식이 올바르지 않아요.", "menuId", null);
        }
    }

    private Scan owned(String userId, String scanId) {
        return scanRepository.findById(scanId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** 전처리(§15.3.1) — 재인코딩으로 EXIF 제거 + 4096px 리사이즈. HEIC은 미지원(400) [데모 결정] */
    private byte[] preprocess(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "JPEG/PNG 이미지를 올려주세요. (HEIC은 데모에서 미지원)", "image", null);
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int edge = Math.max(width, height);
            if (edge > MAX_EDGE) {
                double scale = (double) MAX_EDGE / edge;
                width = (int) Math.round(width * scale);
                height = (int) Math.round(height * scale);
            }
            BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            graphics.drawImage(image, 0, 0, width, height, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpg", out);
            return out.toByteArray();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "이미지를 처리하지 못했어요.", "image", null);
        }
    }

    private AiDtos.ImageMenuAnalysisResult extractWithRetry(byte[] image) {
        AiGateway.AiUnavailableException lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                AiDtos.ImageMenuAnalysisResult result = aiGateway.extractMenus(image);
                if (result != null && result.menus() != null && result.menus().size() <= 40) {
                    return result;
                }
            } catch (AiGateway.AiUnavailableException e) {
                log.warn("analyze-image 실패 (attempt {})", attempt + 1, e);
                lastError = e;
            }
        }
        throw lastError != null ? new ApiException(ErrorCode.AI_UNAVAILABLE)
                : new ApiException(ErrorCode.ANALYSIS_FAILED, "메뉴를 읽지 못했어요 — 다시 찍어주세요.");
    }

    private Map<String, Estimate> estimateBatch(List<String> names, String cuisine) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                List<Estimate> estimates = aiGateway.estimateText(EstimateRequest.menuboard(names, cuisine));
                Map<String, Estimate> resolved = new java.util.LinkedHashMap<>();
                for (Estimate estimate : estimates) {
                    if (estimate.resolved() && estimate.points() != null
                            && estimate.points() >= 0 && estimate.points() <= 999
                            && estimate.basis() != null && estimate.basis().length() <= 40) {
                        resolved.put(estimate.query(), estimate);
                    }
                }
                if (!resolved.isEmpty()) {
                    return resolved;
                }
            } catch (AiGateway.AiUnavailableException e) {
                log.warn("menuboard estimate 실패 (attempt {})", attempt + 1, e);
            }
        }
        throw new ApiException(ErrorCode.AI_UNAVAILABLE);
    }

    private String commentWithRetry(ScanMenu target, ScanMenu compare, Challenge challenge,
                                    int mealsLeft, String place) {
        AiDtos.ScanCommentRequest request = new AiDtos.ScanCommentRequest(
                new AiDtos.ScanCommentMenu(target.getName(), target.getPoints()),
                new AiDtos.ScanCommentMenu(compare.getName(), compare.getPoints()),
                new AiDtos.ScanCommentBudget(challenge.getBalance(), mealsLeft), place);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String comment = aiGateway.scanComment(request);
                // 게이트: 비교 메뉴 언급 · 90자 · 금지 어휘 (§15.8.2)
                if (comment != null && comment.contains(compare.getName())
                        && AiGates.within(comment, 90) && AiGates.clean(comment)) {
                    return comment;
                }
                log.warn("scan-comment 게이트 위반 (attempt {}): {}", attempt + 1, comment);
            } catch (AiGateway.AiUnavailableException e) {
                log.warn("scan-comment 실패 (attempt {})", attempt + 1, e);
            }
        }
        return null; // 실패 시 코멘트 영역 숨김 (§15.8.2)
    }

    private ScanResponse response(Scan scan, List<ScanMenu> menus) {
        Recommendation recommendation = null;
        if (scan.getRecommendedMenuNo() != null) {
            ScanMenu recommended = menus.stream()
                    .filter(m -> m.getMenuNo() == scan.getRecommendedMenuNo())
                    .findFirst().orElse(null);
            if (recommended != null) {
                recommendation = new Recommendation("mnu_" + recommended.getMenuNo(),
                        recommended.getPoints(), scan.getRecommendationComment());
            }
        }
        // 메뉴별로 살아있는 항목을 붙인다 — 흥정 후 4b로 돌아왔을 때 조정값이 보이도록 (§5.3)
        Map<Integer, Item> liveItems = itemRepository
                .findByChallengeIdAndSourceScanIdAndStatusInOrderByCreatedAtDesc(
                        scan.getChallengeId(), scan.getId(), LIVE)
                .stream()
                .collect(Collectors.toMap(Item::getSourceMenuNo, i -> i, (newer, older) -> newer));

        return new ScanResponse(scan.getId(), scan.getPlace(), scan.getPlaceConfidence(), scan.getScannedAt(),
                menus.stream().map(m -> row(m, liveItems.get(m.getMenuNo()))).toList(), recommendation);
    }

    private static MenuRow row(ScanMenu menu) {
        return row(menu, null);
    }

    private static MenuRow row(ScanMenu menu, Item item) {
        MenuItemView itemView = item == null ? null
                : new MenuItemView(item.getId(), item.getStatus(), item.effectivePoints(),
                        item.isHaggled() ? item.getAdjustedLabel() : null, item.isHaggled());
        return new MenuRow("mnu_" + menu.getMenuNo(), menu.getName(), menu.getPoints(), menu.getPm(),
                menu.getConfidence(), menu.getBasis(), menu.isEdited(), itemView);
    }
}
