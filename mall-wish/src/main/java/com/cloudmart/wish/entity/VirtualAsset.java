package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.cloudmart.wish.enums.AssetPayMethod;
import com.cloudmart.wish.enums.AssetType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** ㉞ 虚拟资产配置（Sprint 3.6：配置表化，新增皮肤仅插入配置行）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_virtual_asset")
public class VirtualAsset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 资产类型：SKIN/BGM/SPECIAL_FRUIT */
    private AssetType assetType;

    private String name;

    private String description;

    private String icon;

    private String resourceUrl;

    private Integer priceStarlight;

    private Integer priceRmb;

    private AssetPayMethod payMethod;

    /** 限量库存（0=无限；Redis DECR 原子预扣） */
    private Integer stock;

    /** 库存模式：UNLIMITED / LIMITED（B05：DB 为事实，Redis 仅展示缓存） */
    private String stockMode;

    /** 剩余库存（LIMITED 非负；条件扣减 stock_remaining>0） */
    private Integer stockRemaining;

    /** 乐观锁版本（B05） */
    private Integer version;

    private LocalDateTime validFrom;

    private LocalDateTime validTo;

    private Boolean isActive;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
