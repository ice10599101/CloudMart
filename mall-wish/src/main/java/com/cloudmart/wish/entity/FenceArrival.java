package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 围栏到达记录（Sprint 3.2）：uk(fence, user, date) 幂等——
 * 每用户每围栏每日至多触发一次"到达"（防重复刷绽放）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_fence_arrival")
public class FenceArrival {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 围栏 ID */
    private Long fenceId;

    /** 到达用户 */
    private Long userId;

    /** 触发绽放的心愿 ID */
    private Long wishId;

    /** 到达日期（用户时区日） */
    private LocalDate checkinDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
