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
import java.util.Set;

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
    private static final Map<String, String> TABLE_BY_TYPE = Map.ofEntries(
            Map.entry("job", "pet_job_config"), Map.entry("study", "pet_study_config"),
            Map.entry("career", "pet_career_config"), Map.entry("furniture", "pet_furniture_config"),
            Map.entry("equipment", "pet_equipment_config"), Map.entry("skin", "pet_skin_config"),
            Map.entry("skill", "pet_skill_config"), Map.entry("evolution", "pet_evolution_config"),
            Map.entry("event", "pet_event_config"), Map.entry("daily_quest", "pet_daily_quest_config"),
            Map.entry("sensitive_word", "pet_content_sensitive_word"),
            Map.entry("persona_phrase", "pet_persona_phrase"),
            // F5：宠物数值调整快照留痕（调整写后快照进 pet_config_version 审计）
            Map.entry("pet", "pet"),
            Map.entry("pet_season", "pet_season"),
            Map.entry("food", "pet_food_config"));

    /**
     * R07 运行实体类型：允许快照审计（数值调整/赛季保存留痕），但禁止通用 rollback——
     * 通用回退会把经验、version、主宠标记、赛季 status/settled_at 等运行字段一并写回，
     * 可破坏账实一致与赛季状态机。赛季可编辑配置的受控回退由 R17 的字段白名单实现。
     */
    private static final Set<String> RUNTIME_ENTITY_TYPES = Set.of("pet", "pet_season");

    private final PetConfigVersionMapper versionMapper;
    private final JdbcTemplate jdbcTemplate;

    public PetConfigGovernanceService(PetConfigVersionMapper versionMapper, JdbcTemplate jdbcTemplate) {
        this.versionMapper = versionMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * ADM-03：强类型 DTO 统一校验入口——反射转 Map 后走组合校验，
     * 保证"真实发布路径"与预校验接口共用同一验证器（§4.2）。
     */
    public void validateDto(String configType, Object dto) {
        if (dto == null) {
            return;
        }
        Map<String, Object> data = com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
                .convertValue(dto, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        validate(configType, data);
    }

    /** ADM-03：写后快照 + 版本登记（调用方事务内）；操作人取 mall-admin 透传的可信头 */
    public void snapshotAndRecord(String configType, Long configId, String operator) {
        record(configType, configId, snapshotRow(configType, configId), operator);
    }

    /**
     * 当前管理操作者（P0-3）：从 SecurityContext 中已验签的服务令牌声明读取
     * （mall-admin 签发时注入 admin_username），不再信任可伪造的请求头。
     * 读不到（令牌未携带/部署偏差）返回 "unknown" 并 WARN——审计宁可缺失不可造假。
     */
    public static String currentOperator() {
        try {
            org.springframework.security.core.Authentication authentication =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getDetails()
                    instanceof com.cloudmart.pet.config.PetServiceTokenAuthenticationFilter.ServiceAdminDetails details
                    && details.adminUsername() != null && !details.adminUsername().isBlank()) {
                return details.adminUsername();
            }
        } catch (Exception e) {
            log.debug("操作者上下文不可用（非 Web 线程）", e);
        }
        log.warn("[P0-3] 审计操作者缺失（服务令牌未携带 admin_username），记录为 unknown");
        return "unknown";
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
                checkRange(data, "targetValue", 1, 10000, "目标次数需 1~10000");
                // P1-7：外键型校验——奖励物品编码必须真实存在（pet_equipment_config.code），
                // 否则活动达成后发奖环节静默落空
                Object itemCode = data.get("rewardItemCode");
                if (itemCode != null && !String.valueOf(itemCode).isBlank()) {
                    Long count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM pet_equipment_config WHERE code = ?",
                            Long.class, String.valueOf(itemCode));
                    if (count == null || count == 0) {
                        throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                                "rewardItemCode 不存在（须为已上架装备编码）: " + itemCode);
                    }
                }
                // R17：开始时间必须早于结束时间——按 Instant 比较（字符串比较对非标准格式脆弱）
                Object starts = data.get("startsAt");
                Object ends = data.get("endsAt");
                if (starts != null && ends != null) {
                    java.time.Instant startsAt = parseInstantOrNull(starts);
                    java.time.Instant endsAt = parseInstantOrNull(ends);
                    if (startsAt != null && endsAt != null && !startsAt.isBefore(endsAt)) {
                        throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "开始时间必须早于结束时间");
                    }
                }
            }
            case "furniture", "equipment", "skin" -> {
                checkRange(data, "priceStarlight", 0, 5000, "价格需 0~5000");
                checkRange(data, "requiredLevel", 1, 100, "等级要求需 1~100");
            }
            case "skill" -> {
                // P1-7：数值型——价格/等级上限；effect 必须是服务端已实现枚举（否则配置形同虚设）；
                // effectValue 按 effect 语义校验（比例型 0~1，点数型 0~100），防止配置超大值破坏战斗
                checkRange(data, "priceStarlight", 0, 100000, "价格需 0~100000");
                checkRange(data, "requiredLevel", 1, 100, "等级要求需 1~100");
                Object effect = data.get("effect");
                if (effect != null) {
                    String effectName = String.valueOf(effect);
                    try {
                        com.cloudmart.pet.enums.PetSkillEffect.valueOf(effectName);
                    } catch (IllegalArgumentException e) {
                        throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                                "effect 必须是服务端已实现的效果类型: " + effectName);
                    }
                    checkEffectValue(data.get("effectValue"), effectName);
                }
            }
            case "evolution" -> {
                // P1-7：costStarlight 负数 = 刷币漏洞；阶段必须逐阶递进；加成非负
                checkRange(data, "costStarlight", 0, 100000, "进化费用需 0~100000");
                checkRange(data, "requiredLevel", 1, 100, "等级要求需 1~100");
                checkRange(data, "bonusMaxHp", 0, 10000, "生命上限加成需 0~10000");
                checkRange(data, "bonusStrength", 0, 1000, "力量加成需 0~1000");
                checkRange(data, "bonusIntelligence", 0, 1000, "智力加成需 0~1000");
                checkRange(data, "bonusAgility", 0, 1000, "敏捷加成需 0~1000");
                checkRange(data, "bonusCharm", 0, 1000, "魅力加成需 0~1000");
                Object stageFrom = data.get("stageFrom");
                Object stageTo = data.get("stageTo");
                if (stageFrom != null && stageTo != null
                        && Integer.parseInt(String.valueOf(stageTo))
                                != Integer.parseInt(String.valueOf(stageFrom)) + 1) {
                    throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                            "stageTo 必须等于 stageFrom + 1（逐阶进化，不允许跳阶）");
                }
                // P1-7：外键型校验——解锁皮肤编码必须真实存在
                Object skinCode = data.get("unlockSkinCode");
                if (skinCode != null && !String.valueOf(skinCode).isBlank()) {
                    Long count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM pet_skin_config WHERE code = ?",
                            Long.class, String.valueOf(skinCode));
                    if (count == null || count == 0) {
                        throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                                "unlockSkinCode 不存在（须为已配置皮肤编码）: " + skinCode);
                    }
                }
            }
            default -> { /* 其余类型暂无组合校验规则 */ }
        }
    }

    /** R17：兼容 ISO 带时区/带 Z/本地时间三种形态解析；不可解析返回 null（不误杀） */
    private static java.time.Instant parseInstantOrNull(Object raw) {
        String text = String.valueOf(raw);
        try {
            return java.time.OffsetDateTime.parse(text).toInstant();
        } catch (java.time.format.DateTimeParseException ignored) {
            // 继续尝试其他形态
        }
        try {
            return java.time.Instant.parse(text);
        } catch (java.time.format.DateTimeParseException ignored) {
            // 继续尝试其他形态
        }
        try {
            return java.time.LocalDateTime.parse(text).toInstant(java.time.ZoneOffset.UTC);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 技能效果值语义校验（P1-7）：比例型效果（伤害/成功率/加成比例/减免）取值 0~1，
     * 点数型（QUICK_STEP 先手加成）取值 0~100。
     */
    private void checkEffectValue(Object raw, String effectName) {
        if (raw == null) {
            return;
        }
        double value;
        try {
            value = Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "effectValue 必须是数字");
        }
        // R17：NaN/Infinity 能通过区间比较（NaN 的所有比较均为 false）——必须显式拒绝
        if (!Double.isFinite(value)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR, "effectValue 必须是有限数");
        }
        boolean ratio = !"QUICK_STEP".equals(effectName);
        double max = ratio ? 1.0 : 100.0;
        if (value < 0 || value > max) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "effectValue 需 0~" + (ratio ? "1" : "100") + "（" + effectName + " 语义）");
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

    /**
     * PET-20/T45：删除类配置的原子审计——删除前读 beforeSnapshot，删除与版本登记同事务提交；
     * 删除后行为不可达（修复原实现"先删后查快照必然失败"）。快照即删除 tombstone（删除前终值）。
     */
    @Transactional
    public void deleteAndRecord(String configType, Long configId, String operator, Runnable deletion) {
        String before = snapshotRow(configType, configId);
        deletion.run();
        record(configType, configId, before, operator);
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
        // ADM-02：max+1 并发竞态下 DuplicateKey 不再静默跳过（会丢快照）——有限次重试递增版本
        for (int attempt = 0; attempt < 5; attempt++) {
            PetConfigVersion version = new PetConfigVersion();
            version.setConfigType(configType);
            version.setConfigId(configId);
            version.setVersion(maxVersion + 1 + attempt);
            version.setSnapshot(snapshot);
            version.setOperation("PUBLISH");
            version.setOperator(operator);
            try {
                versionMapper.insert(version);
                return;
            } catch (DuplicateKeyException e) {
                log.debug("配置版本并发冲突，重试: type={}, id={}, version={}",
                        configType, configId, version.getVersion());
            }
        }
        throw new BusinessException(PetErrorCodes.PET_STATE_CONFLICT, "配置版本并发冲突，请重试");
    }

    /** B21 回退：将指定版本的快照字段写回目标行（列白名单取自快照键），并记录 ROLLBACK 版本。
     * R07：运行实体（pet/pet_season）直接拒绝——经验/赛季状态等运行字段不属于可回退配置 */
    @Transactional
    public void rollback(String configType, Long configId, int targetVersion, String operator) {
        if (RUNTIME_ENTITY_TYPES.contains(configType)) {
            throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                    "运行实体不允许通用配置回退: " + configType + "（数值纠错走调整单，赛季状态不可逆）");
        }
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
        // 用 JdbcTemplate 按快照逐列恢复（值走参数绑定防注入；列名按安全字符白名单校验，
        // 防 JSON 快照被篡改后注入——ADM-02 加固）
        List<String> setClauses = new java.util.ArrayList<>();
        List<Object> params = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            if ("id".equals(entry.getKey()) || "created_at".equals(entry.getKey())) {
                continue;
            }
            if (!entry.getKey().matches("^[a-z0-9_]+$")) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "快照含非法列名: " + entry.getKey());
            }
            setClauses.add(entry.getKey() + " = ?");
            params.add(entry.getValue());
        }
        if (setClauses.isEmpty()) {
            return;
        }
        // ADM-02 实锤修复：SET 占位符 N 个 + WHERE 占位符 1 个，params 必须追加 configId
        params.add(configId);
        jdbcTemplate.update("UPDATE `" + table + "` SET " + String.join(", ", setClauses)
                + " WHERE id = ?", params.toArray());
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
