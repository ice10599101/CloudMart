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

    public static <T> ApiResponse<T> okWithCursor(T data, int pageSize, String nextCursor, boolean hasMore) {
        return new ApiResponse<>(true, data, null, Meta.cursor(pageSize, nextCursor, hasMore));
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, ApiError.of(code, message), null);
    }

    public static <T> ApiResponse<T> fail(String code, String message, ApiError error) {
        return new ApiResponse<>(false, null, error != null ? error : ApiError.of(code, message), null);
    }

    /**
     * 分页元数据，同时支持 offset 分页（管理后台）和 cursor 分页（用户端列表）。
     *
     * <p>Offset 分页示例（管理后台）：{@code new Meta(1, 20, 100L)} →
     * {@code {"page":1,"pageSize":20,"total":100}}</p>
     *
     * <p>Cursor 分页示例（用户端）：{@code Meta.cursor(20, "1234567890", true)} →
     * {@code {"pageSize":20,"nextCursor":"1234567890","hasMore":true}}</p>
     *
     * <p>由于 {@link JsonInclude @JsonInclude(NON_NULL)} 作用于整个 ApiResponse，
     * 各字段在 null 时不会序列化，从而保证两种分页互不污染。</p>
     */
    public record Meta(
            Integer page,
            Integer pageSize,
            Long total,
            String nextCursor,
            Boolean hasMore
    ) {
        /** 向后兼容的 offset 分页构造器（保留现有 {@code new Meta(page, pageSize, total)} 调用）。 */
        public Meta(Integer page, Integer pageSize, Long total) {
            this(page, pageSize, total, null, null);
        }

        /** Cursor 分页工厂方法：page 与 total 留空，由 nextCursor/hasMore 表达分页状态。 */
        public static Meta cursor(Integer pageSize, String nextCursor, Boolean hasMore) {
            return new Meta(null, pageSize, null, nextCursor, hasMore);
        }
    }
}
