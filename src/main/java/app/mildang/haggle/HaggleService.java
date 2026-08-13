package app.mildang.haggle;

import app.mildang.ai.AiDtos;
import app.mildang.ai.AiGates;
import app.mildang.ai.AiGateway;
import app.mildang.challenge.Challenge;
import app.mildang.challenge.ChallengeService;
import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import app.mildang.common.id.Ids;
import app.mildang.haggle.HaggleDtos.AgreedView;
import app.mildang.haggle.HaggleDtos.CloseHaggleView;
import app.mildang.haggle.HaggleDtos.CloseResponse;
import app.mildang.haggle.HaggleDtos.MessageResponse;
import app.mildang.haggle.HaggleDtos.OpenRequest;
import app.mildang.haggle.HaggleDtos.OpenResponse;
import app.mildang.haggle.HaggleDtos.ProposalView;
import app.mildang.haggle.HaggleDtos.ReplyView;
import app.mildang.haggle.HaggleDtos.SimRow;
import app.mildang.haggle.HaggleDtos.SimulationView;
import app.mildang.haggle.HaggleDtos.TargetView;
import app.mildang.item.Item;
import app.mildang.item.ItemRepository;
import app.mildang.item.ItemService;
import app.mildang.item.ItemStatus;
import app.mildang.item.SourceType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class HaggleService {

    private static final Logger log = LoggerFactory.getLogger(HaggleService.class);

    /** entryPoint별 칩 4개 — 명세 §7.1. 백엔드 상수 (AI 응답의 chips는 쓰지 않는다) */
    private static final Map<String, List<String>> CHIPS = Map.of(
            "FREE", List.of("반만 먹을게", "너무 적어", "더 깎아줘", "그대로 먹을래"),
            "PROMISE", List.of("반만 먹을게", "메뉴를 내가 못 정해", "그날만 봐줘", "그대로 먹을래"),
            "SCAN", List.of("반만 먹을게", "양은 그대로", "더 깎아줘", "그대로 먹을래"));

    private final HaggleSessionRepository sessionRepository;
    private final HaggleMessageRepository messageRepository;
    private final ItemRepository itemRepository;
    private final ItemService itemService;
    private final ChallengeService challengeService;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public HaggleService(HaggleSessionRepository sessionRepository, HaggleMessageRepository messageRepository,
                         ItemRepository itemRepository, ItemService itemService,
                         ChallengeService challengeService, AiGateway aiGateway, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.itemRepository = itemRepository;
        this.itemService = itemService;
        this.challengeService = challengeService;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OpenResponse open(String userId, OpenRequest request) {
        Challenge challenge = challengeService.requireActive(userId);
        if (!CHIPS.containsKey(request.entryPoint())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "entryPoint가 올바르지 않아요.", "entryPoint", null);
        }
        Item item = itemRepository.findById(request.itemId())
                .filter(i -> i.getChallengeId().equals(challenge.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        switch (item.getStatus()) {
            case PENDING, HAGGLED -> {
            }
            case EXPIRED -> throw new ApiException(ErrorCode.ITEM_EXPIRED);
            default -> throw new ApiException(ErrorCode.ITEM_ALREADY_RECORDED);
        }

        // 항목당 열린 세션 1개 — 기존 세션은 버리고 새로 연다 (재흥정 = 턴 초기화, §7.4)
        sessionRepository.findFirstByItemIdAndStatus(item.getId(), "OPEN")
                .ifPresent(open -> open.setStatus("ABANDONED"));

        Instant now = Instant.now();
        int balance = challenge.getBalance();

        HaggleSession session = new HaggleSession();
        session.setId(Ids.next(Ids.Prefix.HAGGLE));
        session.setItemId(item.getId());
        session.setUserId(userId);
        session.setEntryPoint(request.entryPoint());
        session.setStatus("OPEN");
        session.setTurn(0);
        session.setMaxTurns(10);
        // frame은 개설 시 확정 후 세션 내내 고정 (v1.3 §7.6)
        session.setFrame(balance >= 0 ? "SAVE" : "REDUCE_OVERFLOW");
        session.setTargetName(item.getOriginalName());
        session.setTargetPoints(item.getOriginalPoints());
        session.setTargetPlace(item.getSourceType() == SourceType.IMAGE ? null : "집");
        session.setBalanceAtOpen(balance);
        session.setChipsJson(objectMapper.writeValueAsString(CHIPS.get(request.entryPoint())));
        session.setCreatedAt(now);

        AiDtos.LastAgreement lastAgreement = lastAgreement(userId, item.getOriginalName());
        AiDtos.HaggleOutput opening = callWithGate(buildInput(session, challenge, item, lastAgreement, List.of()),
                item, true);
        session.setOpeningText(opening.text());
        sessionRepository.save(session);

        saveMessage(session, "ASSISTANT", opening.text(), null, challenge, item, false);

        return new OpenResponse(session.getId(), item.getId(), session.getEntryPoint(), 10, 0,
                new TargetView(item.getOriginalName(), item.getOriginalUnit(), item.getOriginalPoints(),
                        item.getOriginalPm(), session.getTargetPlace()),
                null, balance, session.getFrame(), opening.text(),
                CHIPS.get(request.entryPoint()), "OPEN");
    }

    @Transactional
    public MessageResponse message(String userId, String sessionId, String text) {
        Challenge challenge = challengeService.requireActive(userId);
        HaggleSession session = owned(userId, sessionId);
        if (!"OPEN".equals(session.getStatus())) {
            throw new ApiException(ErrorCode.HAGGLE_SESSION_CLOSED);
        }
        if (session.getTurn() >= session.getMaxTurns()) {
            throw new ApiException(ErrorCode.HAGGLE_TURN_EXCEEDED);
        }
        Item item = itemRepository.findById(session.getItemId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        session.setTurn(session.getTurn() + 1);
        saveMessage(session, "USER", text, null, challenge, item, false);

        List<AiDtos.HaggleHistoryItem> history = history(session);
        AiDtos.LastAgreement lastAgreement = lastAgreement(userId, session.getTargetName());
        AiDtos.HaggleOutput output = callWithGate(
                buildInput(session, challenge, item, lastAgreement, history), item, false);
        boolean fallback = output.chips() != null && output.chips().isEmpty(); // 폴백 마커 (아래 fallbackOutput)

        if (output.proposal() != null) {
            session.setAgreedKey(output.proposal().key());
            session.setAgreedLabel(output.proposal().label());
            session.setAgreedPoints(output.proposal().points());
            session.setAgreedBasis(output.proposal().basis());
        }
        saveMessage(session, "ASSISTANT", output.text(), output.proposal(), challenge, item, fallback);

        int balance = challenge.getBalance();
        SimulationView simulation = null;
        ProposalView proposalView = null;
        if (output.proposal() != null) {
            int adjustedAfter = balance - output.proposal().points();
            int originalAfter = balance - item.getOriginalPoints();
            simulation = new SimulationView(
                    new SimRow(output.proposal().label() + " " + output.proposal().points(),
                            adjustedAfter, adjustedAfter < 0),
                    new SimRow(item.getOriginalName() + " " + item.getOriginalUnit() + " " + item.getOriginalPoints(),
                            originalAfter, originalAfter < 0));
            proposalView = new ProposalView(output.proposal().key(), output.proposal().label(),
                    output.proposal().points(), output.proposal().lever(), output.proposal().basis());
        }

        return new MessageResponse(session.getTurn(), session.getMaxTurns(),
                session.getMaxTurns() - session.getTurn(),
                new ReplyView(output.text(), output.proposal() != null),
                proposalView, session.getFrame(), simulation, agreedView(session),
                closeButtonLabel(session, item), session.getStatus());
    }

    /** 합의값을 항목에 반영 — 차감하지 않는다 (§7.3). 잔액 불변은 테스트로 고정 */
    @Transactional
    public CloseResponse close(String userId, String sessionId) {
        Challenge challenge = challengeService.requireActive(userId);
        HaggleSession session = owned(userId, sessionId);
        if ("ABANDONED".equals(session.getStatus())) {
            throw new ApiException(ErrorCode.HAGGLE_SESSION_CLOSED);
        }
        Item item = itemRepository.findById(session.getItemId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        if ("OPEN".equals(session.getStatus())) {
            session.setStatus("CLOSED");
            session.setClosedAt(Instant.now());
            if (session.getAgreedPoints() != null) {
                item.setAdjustedLabel(session.getAgreedLabel());
                item.setAdjustedPoints(session.getAgreedPoints());
                item.setAdjustedBasis(session.getAgreedBasis());
                item.setAdjustedHaggleId(session.getId());
                item.setAdjustedTurns(session.getTurn());
                item.setStatus(ItemStatus.HAGGLED);
            }
        }

        String farewell = session.getAgreedPoints() == null
                ? "그대로 두셨네요. 항목은 원래값 그대로예요 — 기록은 목록에서 «기록하기»로 해주세요."
                : "거래 종료. " + item.getOriginalName() + " " + session.getAgreedLabel() + " "
                        + session.getAgreedPoints() + "으로 항목을 고쳐뒀습니다. 아직 예산은 안 건드렸어요.";

        return new CloseResponse(
                new CloseHaggleView(session.getId(), session.getStatus(), session.getTurn(), session.getClosedAt()),
                itemService.viewOf(item, challenge), farewell);
    }

    /** ✕ 변경 없이 나가기 — ABANDONED, 항목 원래값 유지 (§7.4) */
    @Transactional
    public void abandon(String userId, String sessionId) {
        HaggleSession session = owned(userId, sessionId);
        if ("OPEN".equals(session.getStatus())) {
            session.setStatus("ABANDONED");
        }
    }

    // ---- 내부 ----

    private HaggleSession owned(String userId, String sessionId) {
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getUserId().equals(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private AiDtos.LastAgreement lastAgreement(String userId, String targetName) {
        return sessionRepository
                .findTop10ByUserIdAndTargetNameAndStatusOrderByClosedAtDesc(userId, targetName, "CLOSED")
                .stream()
                .filter(s -> s.getAgreedPoints() != null)
                .findFirst()
                .map(s -> new AiDtos.LastAgreement(s.getAgreedLabel(), s.getAgreedPoints(),
                        (int) Duration.between(s.getClosedAt(), Instant.now()).toDays()))
                .orElse(null);
    }

    private AiDtos.HaggleInput buildInput(HaggleSession session, Challenge challenge, Item item,
                                          AiDtos.LastAgreement lastAgreement,
                                          List<AiDtos.HaggleHistoryItem> history) {
        int dayIndex = challengeService.dayIndex(challenge, Instant.now());
        int daysLeft = Math.max(0, challenge.getTotalDays() - dayIndex);
        AiDtos.AgreedProposal current = session.getAgreedPoints() == null ? null
                : new AiDtos.AgreedProposal(session.getAgreedKey(), session.getAgreedLabel(), session.getAgreedPoints());
        return new AiDtos.HaggleInput(
                new AiDtos.HaggleTarget(item.getOriginalName(), item.getOriginalUnit(),
                        item.getOriginalPoints(), item.getOriginalPm(), item.getOriginalBasis()),
                new AiDtos.HaggleBudget(challenge.getBalance(), challenge.getBudget(),
                        Math.max(1, daysLeft), daysLeft),
                session.getEntryPoint(), session.getFrame(), session.getTurn(), session.getMaxTurns(),
                current, lastAgreement, history);
    }

    private List<AiDtos.HaggleHistoryItem> history(HaggleSession session) {
        List<AiDtos.HaggleHistoryItem> history = new ArrayList<>();
        for (HaggleMessage message : messageRepository.findBySessionIdOrderByIdAsc(session.getId())) {
            history.add(new AiDtos.HaggleHistoryItem(
                    "USER".equals(message.getRole()) ? "user" : "assistant",
                    message.getText(), message.getProposalPoints()));
        }
        return history;
    }

    /** 검증 게이트(§15.8.2) → 재시도 1회 → 규칙 폴백 (AMOUNT 50%) */
    private AiDtos.HaggleOutput callWithGate(AiDtos.HaggleInput input, Item item, boolean opening) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                AiDtos.HaggleOutput output = aiGateway.haggleTurn(input);
                if (passesGate(output, item)) {
                    return output;
                }
                log.warn("haggle-turn 게이트 위반 (attempt {}): {}", attempt + 1, output);
            } catch (AiGateway.AiUnavailableException e) {
                log.warn("haggle-turn AI 호출 실패 (attempt {})", attempt + 1, e);
            }
        }
        return fallbackOutput(input, item, opening);
    }

    private boolean passesGate(AiDtos.HaggleOutput output, Item item) {
        if (output == null || !AiGates.within(output.text(), 90) || !AiGates.clean(output.text())) {
            return false;
        }
        AiDtos.HaggleProposal p = output.proposal();
        if (p == null) {
            return true;
        }
        return p.points() <= item.getOriginalPoints()
                && p.points() >= 0
                && ("AMOUNT".equals(p.lever()) || "COMPOSITION".equals(p.lever()))
                && AiGates.sameMenu(p.label(), item.getOriginalName())
                && AiGates.clean(p.label());
    }

    /** 규칙 폴백 — AMOUNT 절반 (명세 §15.8.2). chips=빈 리스트를 폴백 마커로 사용 */
    private AiDtos.HaggleOutput fallbackOutput(AiDtos.HaggleInput input, Item item, boolean opening) {
        int target = item.getOriginalPoints();
        int balance = input.budget().balance();
        if (opening) {
            String text = item.getOriginalName() + " " + item.getOriginalUnit() + " " + target
                    + "이요. 잔액 " + balance + "니까 그대로면 " + (balance - target) + "입니다.";
            return new AiDtos.HaggleOutput(text, null, List.of());
        }
        int half = Math.max(0, Math.round(target / 2f));
        return new AiDtos.HaggleOutput("절반이면 " + half + "입니다. 여기서 맞춰볼까요?",
                new AiDtos.HaggleProposal("half", "절반", half, "AMOUNT", "양 절반"), List.of());
    }

    private void saveMessage(HaggleSession session, String role, String text,
                             AiDtos.HaggleProposal proposal, Challenge challenge, Item item, boolean fallback) {
        HaggleMessage message = new HaggleMessage();
        message.setSessionId(session.getId());
        message.setTurnNo(session.getTurn());
        message.setRole(role);
        message.setText(text);
        message.setFrame(session.getFrame());
        message.setFallbackUsed(fallback);
        message.setCreatedAt(Instant.now());
        if (proposal != null) {
            message.setHasProposal(true);
            message.setProposalKey(proposal.key());
            message.setProposalLabel(proposal.label());
            message.setProposalPoints(proposal.points());
            message.setProposalLever(proposal.lever());
            message.setProposalBasis(proposal.basis());
            message.setBalanceAfterAdjusted(challenge.getBalance() - proposal.points());
            message.setBalanceAfterOriginal(challenge.getBalance() - item.getOriginalPoints());
        }
        messageRepository.save(message);
    }

    private static AgreedView agreedView(HaggleSession session) {
        return session.getAgreedPoints() == null ? null
                : new AgreedView(session.getAgreedKey(), session.getAgreedLabel(), session.getAgreedPoints());
    }

    private static String closeButtonLabel(HaggleSession session, Item item) {
        return session.getAgreedPoints() == null
                ? "대화 종료 · " + item.getOriginalPoints() + " 그대로"
                : "대화 종료 · " + session.getAgreedPoints() + "으로 반영";
    }
}
