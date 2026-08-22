package com.cloudmart.wish.service.impl;

import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.entity.WishEnvConfig;
import com.cloudmart.wish.entity.WishSpecialEvent;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * 生命树环境 VO 组装工具（TreeEnvService 与 AdminTreeEnvService 共用）。
 */
@Slf4j
public final class TreeEnvAssembler {

    private TreeEnvAssembler() {}

    static SpecialEventVO toEventVO(WishSpecialEvent event) {
        return new SpecialEventVO(event.getId(), event.getEventCode(), event.getTitle(),
                event.getDescription(), event.getStatus(), event.getTriggeredAt(),
                event.getExpiresAt());
    }

    /** visual String → JsonNode（脏数据 Fail-Open 降级 null，不阻断配置读取） */
    static EnvConfigVO toConfigVO(WishEnvConfig config, ObjectMapper objectMapper) {
        JsonNode visual = null;
        if (config.getVisual() != null && !config.getVisual().isBlank()) {
            try {
                visual = objectMapper.readTree(config.getVisual());
            } catch (JsonProcessingException ex) {
                log.warn("环境配置 visual 解析失败（降级 null）: envCode={}, error={}",
                        config.getEnvCode(), ex.getMessage());
            }
        }
        return new EnvConfigVO(config.getId(), config.getEnvCode(), config.getCategory(),
                config.getName(), config.getDescription(), config.getPriority(), visual,
                Boolean.TRUE.equals(config.getIsActive()));
    }

    /**
     * visual 校验并规范化：非空时必须为合法 JSON 对象（渲染参数为 KV
     * 结构，标量/数组无意义），返回紧凑字符串落库。
     *
     * @throws BusinessException TREE_ENV_VISUAL_INVALID（非法 JSON 或非 JSON 对象）
     */
    static String normalizeVisual(String visual, ObjectMapper objectMapper) {
        if (visual == null || visual.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(visual);
            if (!node.isObject()) {
                throw new IllegalArgumentException("visual 必须为 JSON 对象");
            }
            return node.toString();
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new BusinessException(WishErrorCodes.TREE_ENV_VISUAL_INVALID,
                    "visual 必须为合法 JSON 对象: " + ex.getMessage());
        }
    }
}
