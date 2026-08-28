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
 * LBS 功能冻结（Sprint 3.3：连续 3 次可疑 → 24h；管理台可解冻）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_lbs_freeze")
public class LbsFreeze {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 冻结用户（UK） */
    private Long userId;

    /** 冻结原因 */
    private String reason;

    /** 冻结截止（UTC） */
    private LocalDateTime frozenUntil;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
