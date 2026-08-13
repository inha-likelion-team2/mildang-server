package app.mildang.scan;

import java.io.Serializable;

public record ScanMenuId(String scanId, int menuNo) implements Serializable {
    public ScanMenuId() {
        this(null, 0);
    }
}
