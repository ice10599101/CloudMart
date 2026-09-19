package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.vo.PetEventVO;

import java.util.List;

/**
 * 社区宠物活动（原文档 §89 社区宠物活动）。
 *
 * <p>进度<b>惰性统计</b>：直接 COUNT 既有业务表（捞瓶流水/对战/行为留痕），
 * 不在写入路径累加计数——MQ 重投/并发不会造成进度漂移（幂等性来自数据本身）。
 * 领奖以 {@code pet_event_progress.claimed_at} 为幂等标记。</p>
 */
public interface PetEventService {

    /** 活动列表（含我的进度与可领奖状态） */
    List<PetEventVO> events(Long userId);

    /** 活动列表（按已加载的宠物；供提醒服务在用户访问时评估"达成可领奖"，避免重复加载宠物） */
    List<PetEventVO> eventsForPet(Pet pet);

    /** 领取活动奖励（未完成 409 / 已领 409 / 活动已结束 409；星光发放走 mall-wish） */
    PetEventVO claim(Long userId, String eventCode);
}
