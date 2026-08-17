package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.ResourceLogType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 星光流水实体（最终事实来源）。
 *
 * <p>每次星光获取/消耗都写入一条记录，含 delta/source/balanceAfter。
 * {@code wish_user_stat.starlightBalance} 为冗余快照，以此表 SUM(delta) 为对账依据。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_resource_log")
public class WishResourceLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Integer delta;

    private ResourceLogType type;

    private String source;

    private Long refId;

    private Integer balanceAfter;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
