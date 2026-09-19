package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.vo.PetReminderVO;

import java.util.List;

/**
 * 宠物提醒/主动消息服务（复用 mall-notification 通知系统，宠物只是"新的说话方式"，
 * 原文档 §27-33）。主动消息惰性触发（打开宠物页时评估）+ 频控（每日 ≤3 条 + 最小间隔）。
 */
public interface PetReminderService {

    /** 宠物口吻提醒列表（代理 mall-notification type=PET 最近 20 条） */
    List<PetReminderVO> listReminders(Long userId);

    /**
     * 用户打开宠物页时的主动消息触发器（Fail-Open，异常不阻断主流程）：
     * DAILY_GREETING / LONG_ABSENT / PET_HUNGRY / BOTTLE_READY / COMMUNITY_DIGEST（聚合并清零计数）。
     */
    void evaluateOnVisit(Long userId, Pet pet);
}
