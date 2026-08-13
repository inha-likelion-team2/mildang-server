package app.mildang.tip;

import app.mildang.ai.AiDtos;
import app.mildang.ai.AiGates;
import app.mildang.ai.AiGateway;
import app.mildang.challenge.Challenge;
import app.mildang.common.id.Ids;
import app.mildang.common.time.LogicalDate;
import app.mildang.item.Item;
import app.mildang.item.ItemRepository;
import app.mildang.item.ItemStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 한 줄 코멘트 — write_dashboard_tip() (명세 §15.5).
 * signals는 백엔드가 집계해 전달, AI는 문장화만. 검증 실패 시 행을 만들지 않고 null.
 */
@Service
public class TipService {

    private static final Logger log = LoggerFactory.getLogger(TipService.class);
    private static final Set<String> VALID_BASES = Set.of(
            "OVERSPEND_PATTERN", "RECENT_WIN", "PACE_AHEAD", "PACE_BEHIND", "CHECKIN_CORRELATION", "GENERIC");

    private final DashboardTipRepository tipRepository;
    private final ItemRepository itemRepository;
    private final AiGateway aiGateway;

    public TipService(DashboardTipRepository tipRepository, ItemRepository itemRepository, AiGateway aiGateway) {
        this.tipRepository = tipRepository;
        this.itemRepository = itemRepository;
        this.aiGateway = aiGateway;
    }

    /**
     * 오늘 팁 — 배치가 만든 행을 읽고, 없으면 1회 생성 시도(데모 편의) 후 실패 시 null.
     * @return null이면 FE가 영역을 숨긴다 (§3.5)
     */
    @Transactional
    public DashboardTip todayTip(Challenge challenge, int dayIndex, int paceDiff) {
        LocalDate today = LogicalDate.of(Instant.now());
        return tipRepository.findByChallengeIdAndDate(challenge.getId(), today)
                .orElseGet(() -> generate(challenge, today, dayIndex, paceDiff));
    }

    /** 배치·즉석 생성 공용 — 게이트 실패 시 null (행 미생성, 스키마 §10.2) */
    @Transactional
    public DashboardTip generate(Challenge challenge, LocalDate date, int dayIndex, int paceDiff) {
        List<AiDtos.TipSignal> signals = collectSignals(challenge, paceDiff);
        List<String> recentBases = tipRepository.findTop3ByChallengeIdOrderByDateDesc(challenge.getId())
                .stream().map(DashboardTip::getBasis).toList();

        AiDtos.DashboardTipRequest request = new AiDtos.DashboardTipRequest(
                new AiDtos.TipChallenge(challenge.getPeriod().name(), dayIndex,
                        challenge.getBudget(), challenge.getBalance()),
                signals, recentBases);

        Set<String> allowed = new java.util.HashSet<>();
        signals.forEach(s -> allowed.add(s.type()));
        allowed.add("GENERIC");

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                AiDtos.DashboardTip output = aiGateway.dashboardTip(request);
                if (output != null && AiGates.within(output.text(), 90) && AiGates.clean(output.text())
                        && output.basis() != null && VALID_BASES.contains(output.basis())
                        && allowed.contains(output.basis())) {
                    DashboardTip tip = new DashboardTip();
                    tip.setId(Ids.next(Ids.Prefix.TIP));
                    tip.setChallengeId(challenge.getId());
                    tip.setDate(date);
                    tip.setText(output.text());
                    tip.setBasis(output.basis());
                    tip.setCreatedAt(Instant.now());
                    return tipRepository.save(tip);
                }
                log.warn("dashboard-tip 게이트 위반 (attempt {}): {}", attempt + 1, output);
            } catch (AiGateway.AiUnavailableException e) {
                log.warn("dashboard-tip 호출 실패 (attempt {})", attempt + 1, e);
            }
        }
        return null;
    }

    /** signals — SQL 집계 몫 (§15.8.1 #6). RECENT_WIN·OVERSPEND_PATTERN·PACE_* */
    private List<AiDtos.TipSignal> collectSignals(Challenge challenge, int paceDiff) {
        List<AiDtos.TipSignal> signals = new ArrayList<>();
        List<Item> recorded = itemRepository
                .findByChallengeIdAndStatusInOrderByCreatedAtDesc(challenge.getId(), List.of(ItemStatus.RECORDED));

        recorded.stream()
                .filter(Item::isHaggled)
                .findFirst()
                .ifPresent(item -> signals.add(new AiDtos.TipSignal("RECENT_WIN",
                        item.getOriginalName() + " " + item.getOriginalPoints() + "→"
                                + item.getAdjustedPoints() + " 합의")));

        long overspends = recorded.stream()
                .filter(i -> i.getBalanceAfter() != null && i.getBalanceAfter() < 0)
                .count();
        if (overspends >= 2) {
            signals.add(new AiDtos.TipSignal("OVERSPEND_PATTERN", "초과 기록 " + overspends + "건"));
        }
        if (paceDiff > 0) {
            signals.add(new AiDtos.TipSignal("PACE_AHEAD", "페이스보다 +" + paceDiff + " 앞섬"));
        } else if (paceDiff < 0) {
            signals.add(new AiDtos.TipSignal("PACE_BEHIND", "페이스보다 " + paceDiff + " 뒤처짐"));
        }
        return signals;
    }
}
