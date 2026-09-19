package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.vo.PetAchievementVO;

import java.util.List;

/**
 * 宠物成就服务（事件挂载点判定 + 幂等发奖，原文档 §36）。
 */
public interface PetAchievementService {

    /** 触发成就评估的业务事件 */
    enum Event {
        /** 捞瓶结算（CAUGHT/EMPTY 均触发计数检查） */
        BOTTLE_SETTLED,
        /** 对战结算 */
        BATTLE_FINISHED,
        /** 升级 */
        LEVEL_UP,
        /** 聊天 */
        CHAT,
        /** 喂食 */
        FEED,
        /** 清洁 */
        CLEAN,
        /** 打工奖励领取 */
        WORK_CLAIMED,
        /** 读书奖励领取 */
        STUDY_CLAIMED
    }

    /** 我的成就墙（含未达成项，achieved=false 灰显） */
    List<PetAchievementVO> listMy(Long userId);

    /** 事件挂载点：评估全部启用成就，命中即幂等发奖（uk 兜底 + 经验 + MQ 通知） */
    void evaluate(Pet pet, Event event);
}
