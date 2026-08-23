package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.config.WishAiProperties;
import com.cloudmart.wish.dto.AiPromptCreateRequest;
import com.cloudmart.wish.dto.AiPromptStatusUpdateRequest;
import com.cloudmart.wish.entity.WishAiPrompt;
import com.cloudmart.wish.enums.AiPromptScene;
import com.cloudmart.wish.enums.AiPromptStatus;
import com.cloudmart.wish.repository.WishAiPromptMapper;
import com.cloudmart.wish.service.AiPromptService;
import com.cloudmart.wish.vo.AiPromptVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Prompt 模板服务实现（Sprint 2.5）。
 *
 * <p>A/B 分流算法：{@code bucket = floorMod(userId.hashCode(), 100)}，
 * ACTIVE 模板按 id 升序累加 traffic_percent，bucket 落入哪个区间即选中——
 * 同一用户结果稳定，流量比例近似配置值。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiPromptServiceImpl implements AiPromptService {

    /** 缓存 TTL：管理端修改 Prompt 后最迟 1 分钟生效（不重部署） */
    private static final long CACHE_TTL_MS = 60_000L;

    /** A/B 分桶模数（百分比） */
    private static final int BUCKET_MOD = 100;

    private final WishAiPromptMapper promptMapper;
    private final WishAiProperties aiProperties;

    /** scene → (ACTIVE 模板列表, 加载时间戳)；管理端状态变更时主动失效 */
    private final Map<AiPromptScene, CachedPrompts> promptCache = new ConcurrentHashMap<>();

    private record CachedPrompts(List<WishAiPrompt> prompts, long loadedAt) {}

    @Override
    public String getActivePrompt(AiPromptScene scene, Long userId) {
        List<WishAiPrompt> activePrompts = loadActivePrompts(scene);
        if (activePrompts.isEmpty()) {
            return defaultPrompt(scene);
        }
        if (activePrompts.size() == 1 || userId == null) {
            return activePrompts.getFirst().getContent();
        }
        return selectByBucket(activePrompts, userId).getContent();
    }

    @Override
    public List<AiPromptVO> listPrompts(AiPromptScene scene) {
        LambdaQueryWrapper<WishAiPrompt> wrapper = new LambdaQueryWrapper<WishAiPrompt>()
                .eq(scene != null, WishAiPrompt::getScene, scene)
                .orderByDesc(WishAiPrompt::getScene)
                .orderByDesc(WishAiPrompt::getVersion);
        return promptMapper.selectList(wrapper).stream().map(AiPromptVO::from).toList();
    }

    @Override
    public AiPromptVO createPrompt(AiPromptCreateRequest request, Long adminUserId) {
        AiPromptScene scene = request.scene();
        int nextVersion = nextVersionOf(scene);
        validateTrafficPercent(request.trafficPercent());

        WishAiPrompt prompt = new WishAiPrompt();
        prompt.setScene(scene);
        prompt.setVersion(nextVersion);
        prompt.setName(request.name());
        prompt.setContent(request.content());
        prompt.setAbGroup(request.abGroup());
        prompt.setTrafficPercent(request.trafficPercent());
        prompt.setStatus(AiPromptStatus.DRAFT);
        prompt.setRemark(request.remark());
        prompt.setCreatedBy(adminUserId);
        promptMapper.insert(prompt);
        invalidateCache(scene);
        log.info("创建Prompt模板, scene={}, version={}, adminUserId={}", scene, nextVersion, adminUserId);
        return AiPromptVO.from(prompt);
    }

    @Override
    public AiPromptVO updatePromptStatus(Long promptId, AiPromptStatusUpdateRequest request, Long adminUserId) {
        WishAiPrompt prompt = promptMapper.selectById(promptId);
        if (prompt == null) {
            throw new BusinessException(WishErrorCodes.WISH_AI_PROMPT_NOT_FOUND, "Prompt 模板不存在");
        }
        AiPromptStatus target = request.status();
        if (target == AiPromptStatus.ACTIVE) {
            prompt.setTrafficPercent(request.trafficPercent() != null
                    ? request.trafficPercent() : prompt.getTrafficPercent());
            validateTrafficPercent(prompt.getTrafficPercent());
        }
        prompt.setStatus(target);
        prompt.setRemark(request.remark() != null ? request.remark() : prompt.getRemark());
        promptMapper.updateById(prompt);
        invalidateCache(prompt.getScene());
        log.info("更新Prompt状态, id={}, status={}, adminUserId={}", promptId, target, adminUserId);
        return AiPromptVO.from(prompt);
    }

    /**
     * 加载 ACTIVE 模板（60s 缓存；DB 异常时返回上次缓存或空列表→回退默认 Prompt）。
     */
    private List<WishAiPrompt> loadActivePrompts(AiPromptScene scene) {
        CachedPrompts cached = promptCache.get(scene);
        long now = Instant.now().toEpochMilli();
        if (cached != null && now - cached.loadedAt() < CACHE_TTL_MS) {
            return cached.prompts();
        }
        try {
            List<WishAiPrompt> prompts = promptMapper.selectList(new LambdaQueryWrapper<WishAiPrompt>()
                    .eq(WishAiPrompt::getScene, scene)
                    .eq(WishAiPrompt::getStatus, AiPromptStatus.ACTIVE)
                    .orderByAsc(WishAiPrompt::getId));
            promptCache.put(scene, new CachedPrompts(prompts, now));
            return prompts;
        } catch (Exception ex) {
            log.warn("Prompt模板加载失败, scene={}, 使用{}回退", scene,
                    cached != null ? "上次缓存" : "代码默认值", ex);
            return cached != null ? cached.prompts() : List.of();
        }
    }

    /**
     * 稳定分桶选取：userId 哈希对 100 取模，按 traffic_percent 累加区间命中。
     */
    private WishAiPrompt selectByBucket(List<WishAiPrompt> prompts, Long userId) {
        int bucket = Math.floorMod(Long.hashCode(userId), BUCKET_MOD);
        int cumulative = 0;
        for (WishAiPrompt prompt : prompts.stream()
                .sorted(Comparator.comparingLong(WishAiPrompt::getId)).toList()) {
            cumulative += prompt.getTrafficPercent();
            if (bucket < cumulative) {
                return prompt;
            }
        }
        // 流量配置总和 < 100 时兜底：未命中桶的用户使用最后一个模板
        return prompts.getLast();
    }

    private int nextVersionOf(AiPromptScene scene) {
        List<WishAiPrompt> existing = promptMapper.selectList(new LambdaQueryWrapper<WishAiPrompt>()
                .eq(WishAiPrompt::getScene, scene)
                .orderByDesc(WishAiPrompt::getVersion)
                .last("LIMIT 1"));
        return existing.isEmpty() ? 1 : existing.getFirst().getVersion() + 1;
    }

    private void validateTrafficPercent(Integer trafficPercent) {
        if (trafficPercent == null || trafficPercent < 1 || trafficPercent > 100) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR,
                    "流量百分比必须在 1-100 之间");
        }
    }

    private void invalidateCache(AiPromptScene scene) {
        promptCache.remove(scene);
    }

    /**
     * DB 无 ACTIVE 模板时回退代码内默认值（保证空表可用）。
     */
    private String defaultPrompt(AiPromptScene scene) {
        return switch (scene) {
            case GOAL_BREAKDOWN -> aiProperties.getGoalBreakdownSystemPrompt();
            case TREE_HOLE -> aiProperties.getTreeHoleSystemPrompt();
            case ANNUAL_REPORT -> aiProperties.getAnnualReportSystemPrompt();
            case EXPECTED_GUIDE -> aiProperties.getExpectedGuideSystemPrompt();
        };
    }
}
