package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.entity.PetConfigVersion;
import com.cloudmart.pet.repository.PetConfigVersionMapper;
import com.cloudmart.pet.util.PetJsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * B21 配置治理：数值上下限组合校验（校验预览）+ 发布历史快照 + 版本回退。
 *
 * <p>每次管理端 upsert 成功后调用 {@link #record} 自动快照（版本递增）；
 * 回退 {@link #rollback} 将快照字段写回目标行（列名白名单取自快照自身，
 * 防注入）。操作留痕（operator/operation）。</p>
 */
@Service
@Slf4j
public class PetConfigGovernanceService {

    /** 配置类型 → 物理表名（白名单，防注入） */
    private static final Map<String, String> TABLE_BY_TYPE = Map.of(
            "job", "pet_job_config", "study", "pet_study_config",
            "career", "pet_career_config", "furniture", "pet_furniture_config",
            "equipment", "pet_equipment_config", "skin", "pet_skin_config",
            "skill", "pet_skill_config", "evolution", "pet_evolution_config",
            "event", "pet_event_config", "daily_quest", "pet_daily_quest_config");

    private final PetConfigVersionMapper versionMapper;
    private final JdbcTemplate jdbcTemplate;

    public PetConfigGovernanceService(PetConfigVersionMapper versionMapper, JdbcTemplate jdbcTemplate) {
        this.versionMapper = versionMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 数值上下限组合校验（B21：阻止必然无法完成的任务/零成本无限奖励等） */
    public void validate(String configType, Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        switch (configType) {
            case "job", "study", "career" -> {
                checkRange(data, "durationSeconds", 60, 86400, "时长需 60~86400 秒");
                checkRange(data, "expReward", 0, 5000, "经验奖励需 0~5000");
                checkRange(data, "currencyReward", 0, 1000, "星光奖励需 0~1000");
                checkRange(data, "requiredLevel", 1, 100, "等级要求需 1~100");
                checkRange(data, "energyCost", 0, 100, "精力消耗需 0~100");
                checkRange(data, "hungerCost", 0, 100, "饱食消耗需 0~100");
            }
            case "daily_quest" -> {
                checkRange(data, "targetValue", 1, 100, "目标次数需 1~100");
                checkRange(data, "expReward", 0, 500, "经验奖励需 0~500");
                checkRange(data, "currencyReward", 0, 200, "星光奖励需 0~200");
            }
            case "event" -> {
                checkRange(data, "rewardExp", 0, 2000, "经验奖励需 0~2000");
                checkRange(data, "rewardStarlight", 0, 2000, "星光奖励需 0~2000");
                checkRange(data, "rewardAltStarlight", 0, 2000, "替代星光需 0~2000");
                // 开始时间必须早于结束时间（两者均提供时）
                Object starts = data.get("startsAt");
                Object ends = data.get("endsAt");
                if (starts != null && ends != null && String.valueOf(starts).compareTo(String.valueOf(ends)) >= 0) {
                    throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "开始时间必须早于结束时间");
                }
            }
            case "furniture", "equipment", "skin" -> {
                checkRange(data, "priceStarlight", 0, 5000, "价格需 0~5000");
                checkRange(data, "requiredLevel", 1, 100, "等级要求需 1~100");
            }
            default -> { /* 其余类型暂无组合校验规则 */ }
        }
    }

    private void checkRange(Map<String, Object> data, String key, int min, int max, String message) {
        Object value = data.get(key);
        if (value == null) {
            return;
        }
        try {
            int v = Integer.parseInt(String.valueOf(value));
            if (v < min || v > max) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, message);
            }
        } catch (NumberFormatException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, message + "（数值非法）");
        }
    }

    /** 读取目标行快照（SELECT * → JSON），upsert 成功后调用 record 前使用 */
    public String snapshotRow(String configType, Long configId) {
        String table = requireTable(configType);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM `" + table + "` WHERE id = ?", configId);
        if (rows.isEmpty()) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "配置行不存在");
        }
        return PetJsonUtils.toJson(rows.get(0));
    }

    /** 发布快照（B21）：upsert 成功后调用，版本 = 历史最大 +1 */
    @Transactional
    public void record(String configType, Long configId, String snapshot, String operator) {
        String table = requireTable(configType);
        Integer maxVersion = versionMapper.selectList(new LambdaQueryWrapper<PetConfigVersion>()
                        .eq(PetConfigVersion::getConfigType, configType)
                        .eq(PetConfigVersion::getConfigId, configId)
                        .orderByDesc(PetConfigVersion::getVersion)
                        .last("LIMIT 1"))
                .stream().findFirst().map(PetConfigVersion::getVersion).orElse(0);
        PetConfigVersion version = new PetConfigVersion();
        version.setConfigType(configType);
        version.setConfigId(configId);
        version.setVersion(maxVersion + 1);
        version.setSnapshot(snapshot);
        version.setOperation("PUBLISH");
        version.setOperator(operator);
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException e) {
            log.debug("配置版本重复（幂等跳过）: type={}, id={}", configType, configId);
        }
    }

    /** B21 回退：将指定版本的快照字段写回目标行（列白名单取自快照键），并记录 ROLLBACK 版本 */
    @Transactional
    public void rollback(String configType, Long configId, int targetVersion, String operator) {
        String table = requireTable(configType);
        PetConfigVersion target = versionMapper.selectList(new LambdaQueryWrapper<PetConfigVersion>()
                        .eq(PetConfigVersion::getConfigType, configType)
                        .eq(PetConfigVersion::getConfigId, configId)
                        .orderByDesc(PetConfigVersion::getVersion))
                .stream().filter(v -> v.getVersion() == targetVersion).findFirst()
                .orElseThrow(() -> new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "目标版本不存在"));
        Map<String, Object> snapshot = PetJsonUtils.parse(target.getSnapshot(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        if (snapshot == null || !snapshot.containsKey("id")) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "快照缺少 id，无法回退");
        }
        // 用 JdbcTemplate 按快照逐列恢复（列名取自快照自身键，值走参数绑定防注入）
        List<String> setClauses = new java.util.ArrayList<>();
        List<Object> params = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            if ("id".equals(entry.getKey()) || "created_at".equals(entry.getKey())) {
                continue;
            }
            setClauses.add(entry.getKey() + " = ?");
            params.add(entry.getValue());
        }
        if (setClauses.isEmpty()) {
            return;
        }
        jdbcTemplate.update("UPDATE `" + table + "` SET " + String.join(", ", setClauses)
                + " WHERE id = ?", params.stream().toArray());
        // 回退动作本身也留版本审计
        PetConfigVersion version = new PetConfigVersion();
        version.setConfigType(configType);
        version.setConfigId(configId);
        Integer maxVersion = versionMapper.selectList(new LambdaQueryWrapper<PetConfigVersion>()
                        .eq(PetConfigVersion::getConfigType, configType)
                        .eq(PetConfigVersion::getConfigId, configId)
                        .orderByDesc(PetConfigVersion::getVersion)
                        .last("LIMIT 1"))
                .stream().findFirst().map(PetConfigVersion::getVersion).orElse(0);
        version.setVersion(maxVersion + 1);
        version.setSnapshot(target.getSnapshot());
        version.setOperation("ROLLBACK");
        version.setOperator(operator);
        versionMapper.insert(version);
        log.info("配置回退完成: type={}, id={}, version={}", configType, configId, targetVersion);
    }

    /** 历史版本列表 */
    public List<PetConfigVersion> history(String configType, Long configId) {
        return versionMapper.selectList(new LambdaQueryWrapper<PetConfigVersion>()
                .eq(PetConfigVersion::getConfigType, configType)
                .eq(PetConfigVersion::getConfigId, configId)
                .orderByDesc(PetConfigVersion::getVersion)
                .last("LIMIT 50"));
    }

    private String requireTable(String configType) {
        String table = TABLE_BY_TYPE.get(configType);
        if (table == null) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "未知配置类型: " + configType);
        }
        return table;
    }
}
