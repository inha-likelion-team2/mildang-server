package app.mildang.user;

import java.io.Serializable;

public record UserSessionId(String userId, String deviceId) implements Serializable {
    public UserSessionId() {
        this(null, null);
    }
}
