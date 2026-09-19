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
 * 宠物房间摆放（格子坐标唯一：{@code uk_pet_room_item_pos}）。
 *
 * <p>摆放前校验「背包已拥有该家具」（{@code pet_inventory.item_type=FURNITURE}）；
 * 换位 = 卸下再摆放；一件家具可摆放多件的前提是多件持有。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_room_item")
public class PetRoomItem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 宠物 ID */
    private Long petId;

    /** 用户 ID（查询冗余） */
    private Long userId;

    /** 家具编码（pet_furniture_config.code） */
    private String furnitureCode;

    /** 网格 X（0-3） */
    private Integer posX;

    /** 网格 Y（0-2） */
    private Integer posY;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
