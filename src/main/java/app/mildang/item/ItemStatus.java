package app.mildang.item;

public enum ItemStatus {
    PENDING, HAGGLED, EXPIRED, RECORDED, PREPAID, CANCELED;

    /** 아직 확정 전이라 흥정 결과를 반영해도 되는 상태. 차감·취소된 항목은 건드리지 않는다 */
    public boolean isLive() {
        return this == PENDING || this == HAGGLED;
    }
}
