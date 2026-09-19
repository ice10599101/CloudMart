package com.cloudmart.pet.enums;

/**
 * 社区宠物活动统计口径（原文档 §89 社区宠物活动）。
 *
 * <p>进度由既有业务表惰性统计得出，不新增写入路径计数器：
 * BOTTLE→pet_bottle_record、BATTLE→pet_battle、WORK/STUDY/FEED/PLAY/VISIT→pet_activity。</p>
 */
public enum PetEventType {
    /** 捞瓶任务完成次数 */
    BOTTLE,
    /** 对战胜利场次 */
    BATTLE,
    /** 打工领取次数 */
    WORK,
    /** 读书领取次数 */
    STUDY,
    /** 喂食次数 */
    FEED,
    /** 玩耍次数 */
    PLAY,
    /** 串门次数 */
    VISIT
}
