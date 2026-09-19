package com.cloudmart.pet.service;

import com.cloudmart.pet.vo.PetVisitResultVO;
import com.cloudmart.pet.vo.PetVisitVO;

import java.util.List;

/**
 * 宠物串门（原文档 §1.1：宠物去邻居家做客——社区互动 + 养成联动）。
 *
 * <p>串门是宠物替主人进行的轻社交：消耗精力换取心情与经验，邻居主人收到宠物口吻提醒；
 * 同一邻居每日一次、本人每日次数上限由 {@code PetProperties.visit} 控制（Redis 限频，Fail-Open）。</p>
 */
public interface PetVisitService {

    /** 可串门的邻居列表（他人公开宠物，含今日是否已去过） */
    List<PetVisitVO> neighbors(Long userId);

    /** 让主宠去串门（返回串门文案与主宠最新状态） */
    PetVisitResultVO visit(Long userId, Long neighborPetId);
}
