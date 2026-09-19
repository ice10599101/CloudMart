package com.cloudmart.pet.enums;

/**
 * 统一活动类型（原文档 §74：尽量不重新开发一套任务框架）。
 * WORK/STUDY/BOTTLE_FISHING 走完整状态机；REST/FEED/PLAY/CLEAN/VISIT/EVOLVE 为即时行为留痕
 * （直接 CLAIMED，原文档 §10"记录宠物行为"，成就 ACTIVITY_COUNT / 活动进度依此计数）。
 */
public enum PetActivityType {
    /** 打工（pet_job_config） */
    WORK,
    /** 读书（pet_study_config） */
    STUDY,
    /** 捞漂流瓶（时长/冷却在 PetProperties 配置） */
    BOTTLE_FISHING,
    /** 休息（即时结算，不产生 IN_PROGRESS 记录） */
    REST,
    /** 喂食（即时留痕） */
    FEED,
    /** 玩耍（即时留痕） */
    PLAY,
    /** 清洁（即时留痕） */
    CLEAN,
    /** 串门（原文档 §1.1：去邻居家做客，即时留痕） */
    VISIT,
    /** 进化（原文档 §89：达到条件后的形态跃迁，即时留痕） */
    EVOLVE
}
