package app.mildang.analysis;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, String> {
    List<Analysis> findTop20ByUserIdAndResolvedTrueOrderByCreatedAtDesc(String userId);
}
