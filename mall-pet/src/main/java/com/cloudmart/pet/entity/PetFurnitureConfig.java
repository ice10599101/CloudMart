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
 * 家具配置（家园商城在售）。
 *
 * <p>WALL/FLOOR 为房间风格键（穿在 {@code pet_room.wall_code/floor_code}），
 * 其余分类可摆放到房间网格（{@code pet_room_item}），单位舒适度累加成房间舒适度。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_furniture_config")
public class PetFurnitureConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 唯一编码 */
    private String code;

    /** 家具名 */
    private String name;

    /** 描述 */
    private String description;

    /** 分类：WALL/FLOOR/FURNITURE/PLANT/TOY/BED */
    private String category;

    /** 图标（emoji 或 URL） */
    private String icon;

    /** 稀有度：COMMON/RARE/EPIC */
    private String rarity;

    /** 售价（星光） */
    private Integer priceStarlight;

    /** 购买最低等级 */
    private Integer requiredLevel;

    /** 舒适度分值 */
    private Integer comfort;

    /** 是否上架（1 上架/0 下架） */
    private Boolean enabled;

    /** 排序（升序） */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
