package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 品牌许愿池（Sprint 3.6：认领心愿分类 → 用户加入 → 达成发奖）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_brand_pool")
public class BrandPool {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long brandId;

    private Long categoryId;

    private String name;

    private Integer targetCount;

    private Integer currentCount;

    /** 达成奖励 JSON（如 {"starlight":50}） */
    private String rewardJson;

    private LocalDateTime endAt;

    /** ACTIVE/ENDED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
