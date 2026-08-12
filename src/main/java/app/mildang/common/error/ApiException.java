package app.mildang.common.error;

import java.util.Map;

public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final String field;
    private final Map<String, Object> detail;

    public ApiException(ErrorCode code) {
        this(code, code.defaultMessage(), null, null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, null, null);
    }

    public ApiException(ErrorCode code, String message, String field, Map<String, Object> detail) {
        super(message);
        this.code = code;
        this.field = field;
        this.detail = detail;
    }

    public ErrorCode code() {
        return code;
    }

    public String field() {
        return field;
    }

    public Map<String, Object> detail() {
        return detail;
    }
}
