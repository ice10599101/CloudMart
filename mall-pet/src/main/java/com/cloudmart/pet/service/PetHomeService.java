package com.cloudmart.pet.service;

import com.cloudmart.pet.dto.BuyFurnitureRequest;
import com.cloudmart.pet.dto.PlaceFurnitureRequest;
import com.cloudmart.pet.dto.UpdateRoomSettingsRequest;
import com.cloudmart.pet.dto.UpdateRoomThemeRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.vo.PetHomeVO;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetRoomLikeVO;
import com.cloudmart.pet.vo.PetRoomVisitVO;

/**
 * 宠物房间/家园服务（三期）。
 *
 * <p>房间随宠物懒创建（{@code uk_pet_room} 兜底）；家具走 {@code pet_inventory}
 * （item_type = FURNITURE，先入包再扣星光，与商城同一口径），
 * 墙纸/地板是"主题键"（穿在房间上），其余分类占用网格（{@code pet_room_item}，格子唯一）。
 * 舒适度 = 已摆放家具舒适度之和，达阈值后休息有额外心情加成（由互动服务读取）。</p>
 */
public interface PetHomeService {

    /** 我的家园（含回家奖励结算：每日首次进入加心情/经验/亲密度） */
    PetHomeVO home(Long userId);

    /** 购买家具（先入包再扣星光；重复购买 409，等级不够 409） */
    PetInventoryItemVO buyFurniture(Long userId, BuyFurnitureRequest request);

    /** 摆放家具（越界 400 / 格子占用 409 / 未拥有 409） */
    PetHomeVO place(Long userId, PlaceFurnitureRequest request);

    /** 卸下指定格子的家具（舒适度同步重算） */
    PetHomeVO remove(Long userId, Integer posX, Integer posY);

    /** 更换墙纸/地板（必须已拥有且分类匹配） */
    PetHomeVO updateTheme(Long userId, UpdateRoomThemeRequest request);

    /** 修改家园设置（来访开关 / 欢迎语） */
    PetHomeVO updateSettings(Long userId, UpdateRoomSettingsRequest request);

    /** 访问他人家园（串门口径：每日次数上限 + 同一房间每日一次奖励） */
    PetRoomVisitVO visit(Long userId, Long petId);

    /**
     * 好友互访专用入口：跳过"家园每日访问次数"限制（由好友服务自己的每日互访上限约束），
     * 仍保留"同一房间每日只给一次奖励"，供 {@code PetFriendService} 复用同一套房间结算。
     */
    PetRoomVisitVO visitFriendRoom(Long userId, Long friendUserId);

    /** 给他人房间点赞（每日上限 + 每房间每人一次；重复点赞不重复计数） */
    PetRoomLikeVO like(Long userId, Long petId);

    /** 当前舒适度（供互动服务计算休息加成，房间不存在返回 0） */
    int comfortOf(Long petId);

    /** 休息时的额外心情加成（舒适度达到阈值后按比例给，封顶配置值） */
    int comfortRestHappinessBonus(Long petId);

    /** 家园布置埋点（摆放/换主题）：每日任务 DECORATE + 亲密度 ROOM + 成就 ROOM */
    void recordDecorate(Pet pet);
}
