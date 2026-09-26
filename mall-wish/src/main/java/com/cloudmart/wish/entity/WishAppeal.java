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

/** 治理申诉（N01）：同决定同人一条；7 日内提出；复核人不得为原决定处理人。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_appeal")
public class WishAppeal {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long decisionId;

    private Long appellantId;

    private String statement;

    private String evidenceRefs;

    /** PENDING / ACCEPTED / REJECTED */
    private String status;

    private Long reviewerId;

    private String resultReason;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
