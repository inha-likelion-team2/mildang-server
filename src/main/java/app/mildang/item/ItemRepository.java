package app.mildang.item;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, String> {

    List<Item> findByChallengeIdAndStatusInOrderByCreatedAtDesc(String challengeId, List<ItemStatus> statuses);

    List<Item> findByChallengeIdAndKindAndStatusInOrderByCreatedAtDesc(
            String challengeId, ItemKind kind, List<ItemStatus> statuses);
}
