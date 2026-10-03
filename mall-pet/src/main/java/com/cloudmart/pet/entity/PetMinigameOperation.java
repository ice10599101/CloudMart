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
 * 小游戏窗口操作事实（R11/§9.1）：uk(round_id, window_index)——每窗至多一次有效操作，
 * 并发提交/重放由唯一键收敛；successCount 以本表计数为权威（round.ops 仅为展示投影）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_minigame_operation")
public class PetMinigameOperation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long roundId;

    private Long userId;

    /** 时间窗序号（1 基） */
    private Integer windowIndex;

    /** 命中的目标槽位 */
    private String slot;

    private Integer accepted;

    /** 服务端接收时间（UTC，唯一权威） */
    private LocalDateTime serverTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
