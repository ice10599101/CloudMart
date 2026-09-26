package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetActionVO;
import com.cloudmart.pet.vo.PetVO;

import java.util.List;

/**
 * 宠物基础互动：喂食/玩耍/清洁/休息（B06 状态机与防刷）。
 * 全部服务端结算（数值/经验/额度），客户端（Cocos/宿主）只发意图。
 */
public interface PetInteractionService {

    /** 喂食：饥饿+30、心情+5、HP+10、经验+2；每日 5 次有效喂食（数据库额度，按用户共享）；已饱 409 不耗次数 */
    PetVO feed(Long userId);

    /** 玩耍：精力-15、心情+20、经验+8；每日 10 次有收益（数据库额度），超限转无收益动画互动 */
    PetVO play(Long userId);

    /** 清洁：清洁度+40、心情+5、经验+2；清洁度已高 409 */
    PetVO clean(Long userId);

    /**
     * 开始休息（B06：10 分钟定时长期活动）。要求精力/生命至少一项未满（全满 409 且不耗次数）；
     * 到期自动应用恢复（精力回满/HP 回满），本轮无领取步骤；进行中活动互斥。
     */
    PetVO rest(Long userId);

    /** 定时休息到期结算：恢复精力/生命 + 配额内亲密度；供扫描器与读取路径惰性调用（幂等） */
    PetVO settleRest(Long userId);

    /** 动作可执行性查询（B06 统一动作 DTO）：allowed/reasonCode/reasonText/nextAvailableAt/rewardRemainingToday */
    List<PetActionVO> actions(Long userId);
}
