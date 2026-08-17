package app.mildang.item;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, String> {

    List<Item> findByChallengeIdAndStatusInOrderByCreatedAtDesc(String challengeId, List<ItemStatus> statuses);

    List<Item> findByChallengeIdAndKindAndStatusInOrderByCreatedAtDesc(
            String challengeId, ItemKind kind, List<ItemStatus> statuses);

    List<Item> findByStatus(ItemStatus status);

    List<Item> findByChallengeIdIn(List<String> challengeIds);

    /** 스캔 메뉴 한 칸에 딸린 항목들 — 4b 행 표시·항목 재사용에 쓴다 (§6.3 B) */
    List<Item> findByChallengeIdAndSourceScanIdAndStatusInOrderByCreatedAtDesc(
            String challengeId, String sourceScanId, List<ItemStatus> statuses);

    Optional<Item> findFirstByChallengeIdAndSourceScanIdAndSourceMenuNoAndStatusInOrderByCreatedAtDesc(
            String challengeId, String sourceScanId, Integer sourceMenuNo, List<ItemStatus> statuses);

    /** 그날 먹은 것 — 대시보드 today 블록, 날짜 필터 조회 공용 */
    List<Item> findByChallengeIdAndLogicalDateAndStatusInOrderByRecordedAtAsc(
            String challengeId, LocalDate logicalDate, List<ItemStatus> statuses);

    /** 캘린더 — 그 달에 기록이 있는 날을 표시하려면 범위로 한 번에 가져와야 한다 */
    List<Item> findByChallengeIdAndLogicalDateBetweenAndStatusInOrderByLogicalDateAsc(
            String challengeId, LocalDate from, LocalDate to, List<ItemStatus> statuses);
}
