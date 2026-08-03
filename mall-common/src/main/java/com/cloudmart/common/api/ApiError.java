package com.cloudmart.common.api;

import java.util.List;

public record ApiError(
        String code,
        String message,
        List<FieldViolation> details
) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    public static ApiError of(String code, String message) {
        return new ApiError(code, message);
    }

    public static ApiError of(String code, String message, List<FieldViolation> details) {
        return new ApiError(code, message, details);
    }

    public record FieldViolation(
            String field,
            String message
    ) {}
}
