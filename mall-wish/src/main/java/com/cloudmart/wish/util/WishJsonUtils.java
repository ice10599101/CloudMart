package com.cloudmart.wish.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;

/**
 * 心愿模块 JSON 工具类。
 *
 * <p>用于在 entity 的 {@code String mediaUrls/tags}（JSON 字符串）和 VO 的
 * {@code List<String>} 之间转换。MyBatis-Plus 不自动映射 JSON 列到 List，
 * 需手动转换。</p>
 */
public final class WishJsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private WishJsonUtils() {}

    /**
     * 将 JSON 字符串解析为 List<String>。
     *
     * @param json JSON 字符串（如 {@code ["url1","url2"]}）
     * @return 字符串列表；null 或空字符串返回空列表
     */
    public static List<String> parseStringList(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<String> result = MAPPER.readValue(json, STRING_LIST_TYPE);
            return result != null ? result : Collections.emptyList();
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 将 List<String> 序列化为 JSON 字符串。
     *
     * @param list 字符串列表
     * @return JSON 字符串；null 或空列表返回 null（DB 字段允许 NULL）
     */
    public static String stringifyList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
