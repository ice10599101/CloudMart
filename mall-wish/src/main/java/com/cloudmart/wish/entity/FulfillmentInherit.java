package com.cloudmart.wish.entity;

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
 * 还愿传承推送记录（Sprint 2.7，文档 2.8 inherit API）。
 *
 * <p>targetCount 为发起时同求用户数快照（传承触达率的分母）；
 * 一条还愿仅允许一次传承（uk_inherit_fulfillment）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_fulfillment_inherit")
public class FulfillmentInherit {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 心愿 ID */
    private Long wishId;

    /** 还愿记录 ID */
    private Long fulfillmentId;

    /** 发起传承的用户（心愿作者） */
    private Long userId;

    /** 快照：当时同求（SAME_WISH）用户数 */
    private Integer targetCount;

    /** 实际推送成功数 */
    private Integer pushedCount;

    /** 作者附言（可空，≤500 字） */
    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
