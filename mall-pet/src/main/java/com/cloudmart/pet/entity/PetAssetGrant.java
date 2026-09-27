package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 资产发放记录（W01）：uk(sourceType,sourceId,user,rewardSlot)——记录哪一单发了哪个资产。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_asset_grant")
public class PetAssetGrant {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 宠物 ID（可空） */
    private Long petId;

    /** 来源类型：ORDER/CLAIM 等 */
    private String sourceType;

    /** 来源单 ID */
    private Long sourceId;

    /** 奖励槽位 */
    private String rewardSlot;

    /** 物品类型 */
    private String itemType;

    /** 物品编码 */
    private String itemCode;

    /** 数量 */
    private Integer quantity;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}