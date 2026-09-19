package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.CreatePetRequest;
import com.cloudmart.pet.dto.RenamePetRequest;
import com.cloudmart.pet.dto.UpdateAppearanceRequest;
import com.cloudmart.pet.dto.UpdatePrivacyRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.vo.PetPublicVO;
import com.cloudmart.pet.vo.PetVO;

/**
 * 宠物基础服务：领养/查询/改名/外观/隐私/公开资料。
 */
public interface PetService {

    /** 我的宠物（懒更新结算后状态 + 进行中/可领取活动 + 今日喂食余量）；未领养抛 PET_NOT_FOUND */
    PetVO getMyPet(Long userId);

    /** 领养宠物（一期一用户一宠，uk_pet_user 兜底 409） */
    PetVO createPet(Long userId, CreatePetRequest request);

    /** 改名（30 天冷却） */
    PetVO renamePet(Long userId, RenamePetRequest request);

    /** 修改外观（颜色/配饰白名单） */
    PetVO updateAppearance(Long userId, UpdateAppearanceRequest request);

    /** 主页公开开关 */
    void updatePrivacy(Long userId, UpdatePrivacyRequest request);

    /** 他人主页公开宠物卡片（未公开抛 PET_NOT_PUBLIC；宠物不存在抛 PET_NOT_FOUND） */
    PetPublicVO getPublicPet(Long ownerId);

    /** 供其他宠物服务复用：加载用户宠物并结算懒更新（不存在抛 PET_NOT_FOUND） */
    Pet requireOwnedPet(Long userId);
}
