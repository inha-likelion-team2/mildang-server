package app.mildang.common.id;

import com.github.f4b6a3.ulid.UlidCreator;

/** ID 규칙: {prefix}_{ULID} — 명세 §0.7 */
public final class Ids {

    public enum Prefix {
        USER("usr"), CHALLENGE("chl"), ITEM("itm"), ANALYSIS("anl"),
        HAGGLE("hgl"), CHECKIN("chk"), SCAN("scn"), PAYMENT("pay"), TIP("tip"), WEIGHT("wgt");

        private final String code;

        Prefix(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private Ids() {
    }

    public static String next(Prefix prefix) {
        return prefix.code() + "_" + UlidCreator.getUlid();
    }
}
