package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.AuditStrategy;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("wish")
public class Wish {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String title;

    private String description;

    private Long categoryId;

    private WishVisibility visibility;

    private Boolean enableAiReply;

    private AuditStrategy auditStrategy;

    private Boolean triggerEnvEmo;

    private WishStatus status;

    private FruitType fruitType;

    private LocalDateTime expectedAt;

    private LocalDateTime fulfilledAt;

    private String geohash;

    private String mediaUrls;

    private String tags;

    private Integer sameWishCount;

    private Integer lightCount;

    private Integer blessCount;

    private Integer supportCount;

    private AuditStatus auditStatus;

    private Boolean isVisible;

    @TableLogic
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
