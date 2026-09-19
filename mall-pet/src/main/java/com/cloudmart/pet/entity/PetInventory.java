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
 * 宠物背包（装备/皮肤/技能书统一入包，{@code uk_pet_inventory_item} 幂等）。
 *
 * <p>购买即入包；装备/穿戴/学习再消费背包记录。装备与皮肤以 {@code equipped}
 * 标记当前生效项，同一部位多件由服务端保证只有一件处于 {@code equipped=1}。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_inventory")
public class PetInventory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long petId;

    private Long userId;

    /** 物品类型：EQUIPMENT/SKIN/SKILL_BOOK */
    private String itemType;

    /** 物品编码（对应各配置表 code） */
    private String itemCode;

    private Integer quantity;

    /** 是否处于装备/穿戴状态 */
    private Boolean equipped;

    /** 装备部位（装备类型有效） */
    private String slot;

    private LocalDateTime acquiredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
