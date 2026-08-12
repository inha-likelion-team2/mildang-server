package app.mildang.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** 실패 봉투: {"error": {code, message, field?, detail?}} — 성공 응답은 봉투 없음(명세 §0.3). */
public record ErrorResponse(Body error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Body(String code, String message, String field, Map<String, Object> detail) {
    }

    public static ErrorResponse of(ErrorCode code, String message, String field, Map<String, Object> detail) {
        return new ErrorResponse(new Body(code.name(), message, field, detail));
    }
}
