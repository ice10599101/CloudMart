package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.CreatePetRequest;
import com.cloudmart.pet.dto.RenamePetRequest;
import com.cloudmart.pet.dto.UpdateAppearanceRequest;
import com.cloudmart.pet.dto.UpdatePrivacyRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.vo.PetPublicVO;
import com.cloudmart.pet.vo.PetSummaryVO;
import com.cloudmart.pet.vo.PetVO;

import java.util.List;

/**
 * 宠物基础服务：领养/查询/改名/外观/隐私/公开资料 + 多宠物（主宠切换）。
 *
 * <p>多宠物语义（原文档 §37.1 主宠物 / §89 多种宠物）：所有日常玩法（互动/任务/对战/捞瓶）
 * 只作用于<b>主宠</b>，{@link #requireOwnedPet} 即主宠加载入口。</p>
 */
public interface PetService {

    /** 我的宠物（主宠；懒更新结算后状态 + 进行中/可领取活动 + 今日喂食余量）；未领养抛 PET_NOT_FOUND */
    PetVO getMyPet(Long userId);

    /** 领养宠物（多宠物上限由 PetProperties.multiPet.maxPets 控制，超限抛 PET_PET_LIMIT_REACHED） */
    PetVO createPet(Long userId, CreatePetRequest request);

    /** 我的全部宠物（主宠优先），多宠物切换列表 */
    List<PetSummaryVO> listPets(Long userId);

    /** 切换主宠（原主宠自动置为非主宠；非本人宠物抛 PET_NOT_OWNER） */
    PetSummaryVO activatePet(Long userId, Long petId);

    /** 改名（30 天冷却） */
    PetVO renamePet(Long userId, RenamePetRequest request);

    /** 修改外观（颜色/配饰白名单；自定义外观会卸下当前皮肤） */
    PetVO updateAppearance(Long userId, UpdateAppearanceRequest request);

    /** 主页公开开关 */
    void updatePrivacy(Long userId, UpdatePrivacyRequest request);

    /** 他人主页公开宠物卡片（未公开抛 PET_NOT_PUBLIC；宠物不存在抛 PET_NOT_FOUND） */
    PetPublicVO getPublicPet(Long ownerId);

    /** 供其他宠物服务复用：加载用户<b>主宠</b>并结算懒更新（不存在抛 PET_NOT_FOUND） */
    Pet requireOwnedPet(Long userId);
}
