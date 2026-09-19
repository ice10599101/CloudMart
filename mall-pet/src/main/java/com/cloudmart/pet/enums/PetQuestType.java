package com.cloudmart.pet.enums;

/**
 * 每日任务统计口径（与业务埋点一一对应）。
 *
 * <p>埋点原则：进度只在服务端业务写入路径累加（{@code PetDailyQuestService.record}），
 * 客户端只发意图；后台新增任务只需选择已埋点的口径，不需要改代码。</p>
 */
public enum PetQuestType {
    /** 喂食次数 */
    FEED,
    /** 玩耍次数 */
    PLAY,
    /** 清洁次数 */
    CLEAN,
    /** 休息次数 */
    REST,
    /** 打工完成次数 */
    WORK,
    /** 读书完成次数 */
    STUDY,
    /** 捞瓶完成次数 */
    BOTTLE,
    /** 对战完成次数 */
    BATTLE,
    /** 串门次数 */
    VISIT,
    /** 聊天句数 */
    CHAT,
    /** 职业工作完成次数 */
    CAREER_WORK,
    /** 好友互访次数 */
    FRIEND_VISIT,
    /** 留言墙留言条数 */
    WALL_MESSAGE,
    /** 陪伴时长（分钟，按累计分钟数上报） */
    COMPANION,
    /** 家园布置（摆放/换主题）次数 */
    DECORATE
}
