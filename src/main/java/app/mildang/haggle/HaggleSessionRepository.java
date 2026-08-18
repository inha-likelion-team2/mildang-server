package app.mildang.haggle;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HaggleSessionRepository extends JpaRepository<HaggleSession, String> {

    Optional<HaggleSession> findFirstByItemIdAndStatus(String itemId, String status);

    /**
     * 이번 판에 «몇 가지 메뉴로» 대화를 열었나. 같은 항목을 다시 여는 재흥정은 세지 않으려고
     * 세션 수가 아니라 항목 수를 센다 — 마음 바꿀 때마다 횟수가 깎이면 안 된다.
     */
    @org.springframework.data.jpa.repository.Query(
            "select count(distinct s.itemId) from HaggleSession s where s.challengeId = :challengeId")
    long countDistinctItems(@org.springframework.data.repository.query.Param("challengeId") String challengeId);

    List<HaggleSession> findTop10ByUserIdAndTargetNameAndStatusOrderByClosedAtDesc(
            String userId, String targetName, String status);
}
