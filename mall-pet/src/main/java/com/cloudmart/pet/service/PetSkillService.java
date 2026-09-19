package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.LearnSkillRequest;
import com.cloudmart.pet.vo.PetSkillVO;

import java.util.List;

/**
 * 宠物技能（原文档 §89 宠物技能 / §12 技能影响学习与收益）。
 *
 * <p>流程：商城买技能书 → 背包学习 → 技能生效（主动技参与战斗演出，被动技影响
 * 战斗/捞瓶/读书收益，效果由 {@code PetStatsService} 汇总）。</p>
 */
public interface PetSkillService {

    /** 技能列表（全部上架技能 + 我的学习/背包状态 + 服务端生成的效果文案） */
    List<PetSkillVO> skills(Long userId);

    /** 学习技能（需背包已有技能书；uk 幂等，重复学习 409） */
    PetSkillVO learn(Long userId, LearnSkillRequest request);
}
