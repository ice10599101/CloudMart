package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetActivity;
import com.cloudmart.pet.entity.PetContextCounter;
import com.cloudmart.pet.entity.PetMemory;
import com.cloudmart.pet.enums.PetActivityStatus;
import com.cloudmart.pet.enums.PetActivityType;
import com.cloudmart.pet.enums.PetMemoryType;
import com.cloudmart.pet.repository.PetActivityMapper;
import com.cloudmart.pet.repository.PetContextCounterMapper;
import com.cloudmart.pet.repository.PetMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 宠物上下文服务（原文档 §24：宠物 AI 不能随便读取用户隐私）。
 *
 * <p>只组装白名单数据给 AI：宠物公开状态 + 聚合后的社区计数 + 结构化记忆；
 * 不提供任何用户隐私（手机号/地址/私信内容/帖子全文等）。</p>
 */
@Component
@Slf4j
public class PetContextService {

    private final PetContextCounterMapper counterMapper;
    private final PetMemoryMapper memoryMapper;
    private final PetActivityMapper activityMapper;
    private final PetProperties properties;

    public PetContextService(PetContextCounterMapper counterMapper,
                             PetMemoryMapper memoryMapper,
                             PetActivityMapper activityMapper,
                             PetProperties properties) {
        this.counterMapper = counterMapper;
        this.memoryMapper = memoryMapper;
        this.activityMapper = activityMapper;
        this.properties = properties;
    }

    /**
     * 组装 AI 白名单上下文（实施文档 §1.9 第 4 步）。
     */
    public PetContext buildContext(Long userId, Pet pet) {
        PetContextCounter counter = counterMapper.selectOne(new LambdaQueryWrapper<PetContextCounter>()
                .eq(PetContextCounter::getUserId, userId));
        boolean bottleReady = activityMapper.selectCount(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getActivityType, PetActivityType.BOTTLE_FISHING.name())
                .eq(PetActivity::getStatus, PetActivityStatus.COMPLETED.name())) > 0;
        String activitySummary = describeActivity(userId);
        List<PetMemory> memories = memoryMapper.selectList(new LambdaQueryWrapper<PetMemory>()
                .eq(PetMemory::getPetId, pet.getId())
                .orderByDesc(PetMemory::getImportance)
                .last("LIMIT " + properties.getChat().getMemoryLimit()));

        return new PetContext(
                pet.getName(), pet.getLevel(), pet.getPersonality(),
                pet.getHunger(), pet.getHappiness(), pet.getEnergy(), pet.getCleanliness(),
                activitySummary,
                counter != null ? counter.getNewComments() : 0,
                counter != null ? counter.getNewLikes() : 0,
                counter != null ? counter.getNewFollows() : 0,
                counter != null ? counter.getNewCollects() : 0,
                bottleReady,
                memories.stream().map(m -> m.getMemoryKey() + "=" + m.getMemoryValue()).toList());
    }

    /** 社区播报后清零计数（原文档 §31 聚合后即清） */
    public void resetCounters(Long userId) {
        PetContextCounter counter = counterMapper.selectOne(new LambdaQueryWrapper<PetContextCounter>()
                .eq(PetContextCounter::getUserId, userId));
        if (counter != null && (counter.getNewComments() > 0 || counter.getNewLikes() > 0
                || counter.getNewFollows() > 0 || counter.getNewCollects() > 0)) {
            counter.setNewComments(0);
            counter.setNewLikes(0);
            counter.setNewFollows(0);
            counter.setNewCollects(0);
            counterMapper.updateById(counter);
        }
    }

    private String describeActivity(Long userId) {
        PetActivity active = activityMapper.selectOne(new LambdaQueryWrapper<PetActivity>()
                .eq(PetActivity::getUserId, userId)
                .eq(PetActivity::getStatus, PetActivityStatus.IN_PROGRESS.name())
                .last("LIMIT 1"));
        if (active == null) {
            return "空闲中";
        }
        return switch (PetActivityType.valueOf(active.getActivityType())) {
            case WORK -> "正在打工";
            case STUDY -> "正在读书";
            case BOTTLE_FISHING -> "正在海边捞漂流瓶";
            case REST, FEED, PLAY, CLEAN -> "休息中";
        };
    }

    /**
     * AI 白名单上下文（该 record 的字段即 AI 可见的数据边界——超出即违规）。
     */
    public record PetContext(
            String petName,
            Integer level,
            String personality,
            Integer hunger,
            Integer happiness,
            Integer energy,
            Integer cleanliness,
            String activitySummary,
            Integer newComments,
            Integer newLikes,
            Integer newFollows,
            Integer newCollects,
            Boolean bottleReady,
            List<String> memories
    ) {
    }
}
