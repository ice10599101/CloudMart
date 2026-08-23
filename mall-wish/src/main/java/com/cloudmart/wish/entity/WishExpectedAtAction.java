package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.ExpectedActionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 预期管理选项埋点（Sprint 2.5，文档 2.5 数据回收）。
 *
 * <p>记录用户对"心愿到期"通知 3 选项（延长预期/调整目标/转入胶囊）
 * 的选择行为，用于转化率分析；写入不可变，无软删。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_expected_at_action")
public class WishExpectedAtAction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long wishId;

    private ExpectedActionType action;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
