package com.cloudmart.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        Meta meta
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, Meta meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<T> ok(T data, long page, long pageSize, long total) {
        return new ApiResponse<>(true, data, null, new Meta((int) page, (int) pageSize, total));
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, ApiError.of(code, message), null);
    }

    public static <T> ApiResponse<T> fail(String code, String message, ApiError error) {
        return new ApiResponse<>(false, null, error != null ? error : ApiError.of(code, message), null);
    }

    public record Meta(
            Integer page,
            Integer pageSize,
            Long total
    ) {}
}
