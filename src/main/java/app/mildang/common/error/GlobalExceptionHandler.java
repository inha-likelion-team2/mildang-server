package app.mildang.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        return ResponseEntity.status(e.code().status())
                .body(ErrorResponse.of(e.code(), e.getMessage(), e.field(), e.detail()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        var fieldError = e.getBindingResult().getFieldError();
        String field = fieldError != null ? fieldError.getField() : null;
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                        ErrorCode.VALIDATION_FAILED.defaultMessage(), field, null));
    }

    /** 깨진 JSON·잘못된 인코딩 본문 → 400 (500으로 새지 않게) */
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("unreadable request body: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED,
                        "요청 형식을 읽을 수 없어요. JSON과 UTF-8 인코딩을 확인해 주세요.", null, null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.status())
                .body(ErrorResponse.of(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(), null, null));
    }

    /** 10MB 초과 업로드 → 413 IMAGE_TOO_LARGE (명세 §0.4 — 500으로 새지 않게) */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadSize(
            org.springframework.web.multipart.MaxUploadSizeExceededException e) {
        return ResponseEntity.status(ErrorCode.IMAGE_TOO_LARGE.status())
                .body(ErrorResponse.of(ErrorCode.IMAGE_TOO_LARGE,
                        ErrorCode.IMAGE_TOO_LARGE.defaultMessage(), "image", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR,
                        ErrorCode.INTERNAL_ERROR.defaultMessage(), null, null));
    }
}
