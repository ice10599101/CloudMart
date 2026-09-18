package com.cloudmart.pet.enums;

/**
 * 统一活动类型：打工/读书/捞瓶/休息共用 pet_activity 一张表 + 一套状态机
 *（原文档 §74：尽量不重新开发一套任务框架）。
 */
public enum PetActivityType {
    /** 打工（pet_job_config） */
    WORK,
    /** 读书（pet_study_config） */
    STUDY,
    /** 捞漂流瓶（时长/冷却在 PetProperties 配置） */
    BOTTLE_FISHING,
    /** 休息（即时结算，不产生 IN_PROGRESS 记录） */
    REST
}
