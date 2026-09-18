package com.cloudmart.pet.enums;

/**
 * 宠物状态快照（冗余展示字段；权威状态以 pet_activity 中 IN_PROGRESS 记录为准，
 * 查询时由服务端合成，避免状态机双写不一致）。
 */
public enum PetStatus {
    /** 闲置 */
    IDLE,
    /** 打工中 */
    WORKING,
    /** 读书中 */
    STUDYING,
    /** 捞瓶中 */
    FISHING,
    /** 休息中（即时动作，不落活动记录，仅展示） */
    RESTING
}
