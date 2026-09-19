package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.RequestRelationRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.enums.PetRelationAction;
import com.cloudmart.pet.vo.PetRelationPanelVO;
import com.cloudmart.pet.vo.PetRelationVO;

/**
 * 宠物关系服务（三期）：情侣（1v1 独占）/ 闺蜜 / 兄弟 / 死党。
 *
 * <p>关系由"发起 → 对方主人确认"建立（{@code uk_pet_relation} 幂等），
 * 建立后关系亲密度由双方互访、互相留言、彼此对战累积（{@link #gainBetween}，
 * 由好友/留言墙/对战在自己的写入路径调用，每日有上限防互刷）。</p>
 */
public interface PetRelationService {

    /** 关系面板（已建立 + 收到申请 + 我发起的 + 候选） */
    PetRelationPanelVO panel(Long userId);

    /** 发起关系申请（独占类型已有一段时 409；重复申请 409） */
    PetRelationVO request(Long userId, RequestRelationRequest request);

    /** 同意申请（只有接收方主人可确认） */
    PetRelationVO accept(Long userId, Long relationId);

    /** 拒绝申请 */
    PetRelationVO reject(Long userId, Long relationId);

    /** 解除关系（任一方可解除，保留数据置 DISSOLVED） */
    PetRelationVO dissolve(Long userId, Long relationId);

    /**
     * 行为埋点：两只宠物之间存在 ACTIVE 关系时给关系加亲密度（双向同一行，只加一次）。
     * 无关系 / 达到每日上限时静默跳过，绝不阻断主玩法。
     */
    void gainBetween(Pet pet, Pet otherPet, PetRelationAction action);
}
