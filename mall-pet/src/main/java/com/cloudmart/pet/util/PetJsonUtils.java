package com.cloudmart.pet.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;

/**
 * 宠物模块 JSON 工具（外观/快照/回合流水/活动结果均以 JSON 列存储）。
 * 解析失败按业务错误处理（400），不静默吞异常。
 *
 * <p>MAPPER 必须注册 JavaTimeModule：配置快照（snapshotRow）经 queryForList
 * 携带 LocalDateTime/Timestamp 列值，裸 mapper 序列化 java.time 类型直接抛
 * InvalidDefinitionException（远程验收发现的 F5/F2 管理端 400 根因）。
 * 时间输出与 JacksonConfig HTTP 契约对齐（RFC 3339 UTC 字符串）。</p>
 */
public final class PetJsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private PetJsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "JSON 序列化失败", e);
        }
    }

    public static <T> T parse(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "JSON 解析失败", e);
        }
    }
}
