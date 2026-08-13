package app.mildang.scan;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanMenuRepository extends JpaRepository<ScanMenu, ScanMenuId> {
    List<ScanMenu> findByScanIdOrderBySortOrderAsc(String scanId);
}
