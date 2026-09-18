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
 * 社区事件计数器（宠物 AI 上下文专用白名单数据）。
 *
 * <p>mall-pet 自建消费组消费 community-events 维护计数；用户打开宠物页或
 * 拉取提醒后清零（聚合成一条播报，原文档 §31 通知聚合）。
 * 不重复落通知——通知落库由 mall-notification 自己的消费组完成。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_context_counter")
public class PetContextCounter {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 一用户一行（uk_pet_context_user） */
    private Long userId;

    private Integer newComments;

    private Integer newLikes;

    private Integer newFollows;

    private Integer newCollects;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
