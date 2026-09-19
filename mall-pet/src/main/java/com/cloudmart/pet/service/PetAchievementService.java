package com.cloudmart.pet.service;

import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.vo.PetAchievementVO;

import java.util.List;
import java.util.Set;

/**
 * 宠物成就服务（事件挂载点判定 + 幂等发奖，原文档 §36）。
 */
public interface PetAchievementService {

    /**
     * 触发成就评估的业务事件。
     *
     * <p>每个事件声明自己"可能改变"的判定维度 ({@code conditionType}) 与行为子类型
     * ({@code conditionSubtype})，评估时据此裁剪候选集：一次喂食不会去 COUNT 捞瓶流水，
     * 避免每次互动都全量扫描全部成就造成的 N+1 查询放大。</p>
     */
    enum Event {
        /** 捞瓶结算（CAUGHT/EMPTY 均触发计数检查） */
        BOTTLE_SETTLED(Set.of("BOTTLE_COUNT"), Set.of()),
        /** 对战结算 */
        BATTLE_FINISHED(Set.of("BATTLE_WIN"), Set.of()),
        /** 升级（等级门槛 + 升级带来的属性成长可能触发满属性类成就） */
        LEVEL_UP(Set.of("LEVEL", "STATS_FULL"), Set.of()),
        /** 聊天 */
        CHAT(Set.of("CHAT_COUNT"), Set.of()),
        /** 喂食 */
        FEED(Set.of("ACTIVITY_COUNT"), Set.of("FEED")),
        /** 清洁 */
        CLEAN(Set.of("ACTIVITY_COUNT"), Set.of("CLEAN")),
        /** 打工奖励领取 */
        WORK_CLAIMED(Set.of("ACTIVITY_COUNT"), Set.of("WORK")),
        /** 读书奖励领取（智力成长可能触发满属性类成就） */
        STUDY_CLAIMED(Set.of("ACTIVITY_COUNT", "STATS_FULL"), Set.of("STUDY")),
        /** 玩耍留痕（当前无对应成就，保留挂载点便于后台新增成就后立即生效） */
        PLAY(Set.of("ACTIVITY_COUNT"), Set.of("PLAY")),
        /** 休息留痕 */
        REST(Set.of("ACTIVITY_COUNT"), Set.of("REST")),
        /** 串门（原文档 §1.1 宠物串门）：串门次数类成就 */
        VISIT(Set.of("VISIT_COUNT"), Set.of("VISIT")),
        /** 进化（原文档 §89 宠物进化）：进化阶数类成就 */
        EVOLUTION(Set.of("EVOLUTION"), Set.of());

        private final Set<String> conditionTypes;
        private final Set<String> activitySubtypes;

        Event(Set<String> conditionTypes, Set<String> activitySubtypes) {
            this.conditionTypes = conditionTypes;
            this.activitySubtypes = activitySubtypes;
        }

        /** 该事件是否可能改变指定成就的判定结果 */
        public boolean concerns(String conditionType, String conditionSubtype) {
            if (conditionType == null || !conditionTypes.contains(conditionType)) {
                return false;
            }
            if (!"ACTIVITY_COUNT".equals(conditionType)) {
                return true;
            }
            return conditionSubtype != null && activitySubtypes.contains(conditionSubtype);
        }
    }

    /** 我的成就墙（含未达成项，achieved=false 灰显） */
    List<PetAchievementVO> listMy(Long userId);

    /**
     * 事件挂载点：按事件维度裁剪候选后评估，命中即幂等发奖（uk 兜底 + 经验 + MQ 通知）。
     * 入参 event 决定"哪些成就值得重新 COUNT"，不可为空。
     */
    void evaluate(Pet pet, Event event);
}
