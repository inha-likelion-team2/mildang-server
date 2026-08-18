package app.mildang.common.error;

import org.springframework.http.HttpStatus;

/**
 * API 명세 v1.3 §0.4 에러 코드. message는 사용자 노출 가능 한국어 기본값이며
 * 던지는 쪽에서 더 구체적인 문구로 덮어쓸 수 있다.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "로그인이 만료됐어요. 다시 이어갈게요."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
    PAYMENT_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "이 기간은 결제가 필요해요."),
    PAYMENT_VERIFICATION_FAILED(HttpStatus.PAYMENT_REQUIRED, "결제 확인에 실패했어요. 다시 시도해 주세요."),
    PAYMENT_ALREADY_USED(HttpStatus.CONFLICT, "이미 사용된 결제예요."),
    FREE_TRIAL_USED(HttpStatus.FORBIDDEN, "무료 체험을 이미 쓰셨어요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요."),
    CHALLENGE_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 챌린지가 있어요."),
    BUDGET_ALREADY_SET(HttpStatus.CONFLICT, "이미 예산이 확정된 챌린지입니다."),
    CHALLENGE_NOT_COMPLETED(HttpStatus.CONFLICT, "아직 완주 전이에요."),
    ITEM_ALREADY_RECORDED(HttpStatus.CONFLICT, "이미 기록된 항목이에요."),
    HAGGLE_TURN_EXCEEDED(HttpStatus.CONFLICT, "흥정은 10턴까지예요. 이제 정리해 볼까요?"),
    HAGGLE_QUOTA_EXCEEDED(HttpStatus.CONFLICT, "이번 판 밀당은 다 썼어요. 기록과 선차감은 그대로 됩니다."),
    HAGGLE_SESSION_CLOSED(HttpStatus.CONFLICT, "이미 끝난 흥정이에요."),
    ITEM_EXPIRED(HttpStatus.CONFLICT, "지난 항목으로는 흥정을 열 수 없어요."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "이미지는 10MB 이하로 올려주세요."),
    ANALYSIS_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "잘 모르겠어요 — 비슷한 걸 골라주세요"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "잠시 뒤에 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "문제가 생겼어요. 잠시 뒤에 다시 시도해 주세요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "밀당이가 잠깐 자리를 비웠어요. 곧 돌아올게요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
