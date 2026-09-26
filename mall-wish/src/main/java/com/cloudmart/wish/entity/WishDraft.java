package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 心愿草稿（N03）：仅本人可见，不进公共 feed、不发奖励；client_draft_id 支撑断网恢复不重复建。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_draft")
public class WishDraft {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String clientDraftId;

    private String title;

    private String description;

    private Long categoryId;

    private String mediaUrls;

    private String tags;

    private LocalDateTime expectedAt;

    private String expectedTimezone;

    private String visibility;

    /** 发布后的心愿 ID（同事务唯一关联保障发布幂等） */
    private Long publishedWishId;

    private Integer version;

    @TableLogic
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
