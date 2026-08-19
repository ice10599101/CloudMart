package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.TreeEnvironment;
import com.cloudmart.wish.enums.TreeEnvSource;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 世界生命树全局环境状态（文档 2.2 / Sprint 2.2，单行表 id 恒为 1）。
 *
 * <p>隐私约束（文档 2.2）：聚合情绪分数不落库，仅存 Redis
 * {@code wish:tree:mood}（TTL 10 分钟）；本表 {@code sampleCount} 仅为
 * 计数，不含情绪明细。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_world_tree_state")
public class WishWorldTreeState {

    /** 固定单行主键 */
    public static final long SINGLETON_ID = 1L;

    @TableId
    private Long id;

    private TreeEnvironment environment;

    private TreeEnvSource environmentSource;

    /** 当前环境触发时间；RAIN 续雨时保持首次触发时间（最短持续基准） */
    private LocalDateTime triggeredAt;

    /** 当前环境过期时间；NULL 表示无固定过期（RAIN 持续至扫描复评） */
    private LocalDateTime expiresAt;

    /** 最近一次情绪扫描时间 */
    private LocalDateTime lastScanAt;

    /** 最近一次扫描聚合样本数（仅计数，无情绪明细） */
    private Integer sampleCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
