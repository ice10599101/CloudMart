package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.AdminEnvConfigRequest;
import com.cloudmart.wish.dto.TriggerSpecialEventRequest;
import com.cloudmart.wish.entity.WishEnvConfig;
import com.cloudmart.wish.entity.WishSpecialEvent;
import com.cloudmart.wish.enums.EnvCategory;
import com.cloudmart.wish.enums.SpecialEventStatus;
import com.cloudmart.wish.repository.WishEnvConfigMapper;
import com.cloudmart.wish.repository.WishSpecialEventMapper;
import com.cloudmart.wish.service.AdminTreeEnvService;
import com.cloudmart.wish.vo.EnvConfigVO;
import com.cloudmart.wish.vo.SpecialEventVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端生命树环境服务实现（文档 Sprint 2.2 特殊事件触发台 + 环境配置管理）。
 *
 * <p><b>单活跃事件语义</b>：triggerSpecialEvent 在同一事务内先结束全部
 * ACTIVE 事件再插入新事件（应用层保证，见 V10 迁移决策 2）；结束/过期
 * 事件行保留供审计（无物理删除）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTreeEnvServiceImpl implements AdminTreeEnvService {

    /** 事件列表条数上限（防误传大值拖库） */
    private static final int MAX_EVENT_LIST_LIMIT = 200;

    private final WishSpecialEventMapper specialEventMapper;
    private final WishEnvConfigMapper envConfigMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public SpecialEventVO triggerSpecialEvent(TriggerSpecialEventRequest request, Long adminUserId) {
        WishEnvConfig config = envConfigMapper.selectOne(new LambdaQueryWrapper<WishEnvConfig>()
                .eq(WishEnvConfig::getEnvCode, request.getEventCode())
                .eq(WishEnvConfig::getIsActive, true)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BusinessException(WishErrorCodes.TREE_ENV_CONFIG_NOT_FOUND,
                    "事件代码无启用的环境配置: " + request.getEventCode());
        }

        // 单活跃事件：结束既有 ACTIVE 事件（同事务，含已过期未惰性回收的行）
        specialEventMapper.update(null, new LambdaUpdateWrapper<WishSpecialEvent>()
                .eq(WishSpecialEvent::getStatus, SpecialEventStatus.ACTIVE)
                .set(WishSpecialEvent::getStatus, SpecialEventStatus.ENDED));

        LocalDateTime now = LocalDateTime.now();
        WishSpecialEvent event = new WishSpecialEvent();
        event.setEventCode(request.getEventCode());
        event.setTitle(request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle() : config.getName());
        event.setDescription(request.getDescription() != null && !request.getDescription().isBlank()
                ? request.getDescription() : config.getDescription());
        event.setStatus(SpecialEventStatus.ACTIVE);
        event.setTriggeredBy(adminUserId);
        event.setTriggeredAt(now);
        event.setExpiresAt(request.getDurationMinutes() != null
                ? now.plusMinutes(request.getDurationMinutes()) : null);
        specialEventMapper.insert(event);

        log.info("管理员触发全站特殊事件: id={}, code={}, admin={}, expiresAt={}",
                event.getId(), event.getEventCode(), adminUserId, event.getExpiresAt());
        return TreeEnvAssembler.toEventVO(event);
    }

    @Override
    @Transactional
    public SpecialEventVO endSpecialEvent(Long eventId) {
        WishSpecialEvent event = specialEventMapper.selectById(eventId);
        if (event == null) {
            throw new BusinessException(WishErrorCodes.TREE_SPECIAL_EVENT_NOT_FOUND,
                    "特殊事件不存在: " + eventId);
        }
        if (event.getStatus() == SpecialEventStatus.ACTIVE) {
            // status=ACTIVE 条件双保险幂等（并发结束/惰性过期竞争安全）
            specialEventMapper.update(null, new LambdaUpdateWrapper<WishSpecialEvent>()
                    .eq(WishSpecialEvent::getId, eventId)
                    .eq(WishSpecialEvent::getStatus, SpecialEventStatus.ACTIVE)
                    .set(WishSpecialEvent::getStatus, SpecialEventStatus.ENDED));
            event.setStatus(SpecialEventStatus.ENDED);
            log.info("管理员手动结束特殊事件: id={}, code={}", eventId, event.getEventCode());
        }
        return TreeEnvAssembler.toEventVO(event);
    }

    @Override
    public List<SpecialEventVO> listSpecialEvents(int limit) {
        int bounded = Math.clamp(limit, 1, MAX_EVENT_LIST_LIMIT);
        return specialEventMapper.selectList(new LambdaQueryWrapper<WishSpecialEvent>()
                        .orderByDesc(WishSpecialEvent::getTriggeredAt)
                        .last("LIMIT " + bounded))
                .stream().map(TreeEnvAssembler::toEventVO).toList();
    }

    @Override
    public List<EnvConfigVO> listEnvConfigs() {
        return envConfigMapper.selectList(new LambdaQueryWrapper<WishEnvConfig>()
                        .orderByDesc(WishEnvConfig::getPriority)
                        .orderByAsc(WishEnvConfig::getEnvCode))
                .stream().map(c -> TreeEnvAssembler.toConfigVO(c, objectMapper)).toList();
    }

    @Override
    @Transactional
    public EnvConfigVO createEnvConfig(AdminEnvConfigRequest request) {
        String visual = TreeEnvAssembler.normalizeVisual(request.getVisual(), objectMapper);
        if (envConfigMapper.selectCount(new LambdaQueryWrapper<WishEnvConfig>()
                .eq(WishEnvConfig::getEnvCode, request.getEnvCode())) > 0) {
            throw new BusinessException(WishErrorCodes.TREE_ENV_CONFIG_CODE_DUPLICATED,
                    "环境代码已存在: " + request.getEnvCode());
        }
        WishEnvConfig config = new WishEnvConfig();
        config.setEnvCode(request.getEnvCode());
        config.setCategory(EnvCategory.valueOf(request.getCategory()));
        config.setName(request.getName());
        config.setDescription(request.getDescription());
        config.setPriority(request.getPriority());
        config.setVisual(visual);
        config.setIsActive(request.getActive() == null || request.getActive());
        try {
            envConfigMapper.insert(config);
        } catch (DuplicateKeyException ex) {
            // 并发新增同 code 兜底：uk_env_code 唯一索引
            throw new BusinessException(WishErrorCodes.TREE_ENV_CONFIG_CODE_DUPLICATED,
                    "环境代码已存在: " + request.getEnvCode());
        }
        log.info("新增环境配置: code={}, name={}", config.getEnvCode(), config.getName());
        return TreeEnvAssembler.toConfigVO(config, objectMapper);
    }

    @Override
    @Transactional
    public EnvConfigVO updateEnvConfig(Long configId, AdminEnvConfigRequest request) {
        WishEnvConfig config = envConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(WishErrorCodes.TREE_ENV_CONFIG_NOT_FOUND,
                    "环境配置不存在: " + configId);
        }
        if (!config.getEnvCode().equals(request.getEnvCode())) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "环境代码不可修改（天气/季节/事件链路关联键）");
        }
        String visual = TreeEnvAssembler.normalizeVisual(request.getVisual(), objectMapper);
        config.setCategory(EnvCategory.valueOf(request.getCategory()));
        config.setName(request.getName());
        config.setDescription(request.getDescription());
        config.setPriority(request.getPriority());
        config.setVisual(visual);
        config.setIsActive(request.getActive() == null || request.getActive());
        envConfigMapper.updateById(config);
        log.info("编辑环境配置: id={}, code={}", configId, config.getEnvCode());
        return TreeEnvAssembler.toConfigVO(config, objectMapper);
    }

    @Override
    @Transactional
    public EnvConfigVO updateEnvConfigStatus(Long configId, boolean active) {
        WishEnvConfig config = envConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(WishErrorCodes.TREE_ENV_CONFIG_NOT_FOUND,
                    "环境配置不存在: " + configId);
        }
        config.setIsActive(active);
        envConfigMapper.updateById(config);
        log.info("环境配置{}: id={}, code={}", active ? "上架" : "下架", configId, config.getEnvCode());
        return TreeEnvAssembler.toConfigVO(config, objectMapper);
    }
}
