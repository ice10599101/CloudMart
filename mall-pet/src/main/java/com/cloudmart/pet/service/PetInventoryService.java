package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.EquipItemRequest;
import com.cloudmart.pet.dto.WearSkinRequest;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetVO;

import java.util.List;

/**
 * 宠物背包与穿戴（原文档 §6.1 宠物背包/装备、§89 宠物皮肤）。
 *
 * <p>穿戴类操作都返回主宠最新 {@link PetVO}：装备会改变战斗/捞瓶属性、
 * 皮肤会改变外观，前端需要一个统一的"刷新后状态"入口（避免前端自行拼装数值）。</p>
 */
public interface PetInventoryService {

    /** 背包列表（装备/皮肤/技能书，含装备/穿戴状态） */
    List<PetInventoryItemVO> inventory(Long userId);

    /** 穿戴装备（同部位自动卸下旧装备） */
    PetVO equip(Long userId, EquipItemRequest request);

    /** 卸下指定部位装备（slot: HAT/NECKLACE/SCARF/BACKPACK） */
    PetVO unequip(Long userId, String slot);

    /** 穿戴皮肤（写入 appearance + skinCode；同类型皮肤互斥） */
    PetVO wearSkin(Long userId, WearSkinRequest request);

    /** 卸下皮肤，恢复种类原生外观 */
    PetVO removeSkin(Long userId);
}
