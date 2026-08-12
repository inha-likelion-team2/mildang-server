package app.mildang.challenge;

import app.mildang.common.error.ApiException;
import app.mildang.common.error.ErrorCode;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** 주당 횟수 구간 — API 표기는 "0-1" / "2-3" / "4+" (명세 §3.3) */
public enum SurveyLevel {
    LOW("0-1"), MID("2-3"), HIGH("4+");

    private final String api;

    SurveyLevel(String api) {
        this.api = api;
    }

    @JsonValue
    public String api() {
        return api;
    }

    @JsonCreator
    public static SurveyLevel fromApi(String value) {
        for (SurveyLevel level : values()) {
            if (level.api.equals(value)) {
                return level;
            }
        }
        throw new ApiException(ErrorCode.VALIDATION_FAILED, "설문 값은 0-1 / 2-3 / 4+ 중 하나예요.", "survey", null);
    }
}
