package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.EncounterLetterStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 相遇信笺（Sprint 3.3，文档 1.2 ⑮b）：一次匹配生成两条镜像信笺。
 *
 * <p>status 状态机 PENDING → DELIVERED（deliver_after 到期 + 匿名通知）
 * → READ（用户拆信）。peer 字段仅服务端使用，VO 层永不外露（匿名验收）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_encounter_letter")
public class EncounterLetter {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 信笺归属用户 */
    private Long ownerUserId;

    /** 对方用户（匿名展示，VO 不外露） */
    private Long peerUserId;

    /** 对方心愿 ID（点亮互动目标） */
    private Long peerWishId;

    /** 相遇网格（geohash6，约 1.2km） */
    private String geohash6;

    /** 30 分钟时间桶（向下取整，UTC） */
    private LocalDateTime timeBucket;

    /** 双方重叠心愿标签快照（JSON 数组） */
    private String wishTags;

    /** 相遇时刻（UTC，桶内实际时间） */
    private LocalDateTime encounterTime;

    /** 诗意文案（PENDING 时对用户返回 null） */
    private String content;

    /** 状态机 */
    private EncounterLetterStatus status;

    /** 延迟投递时间（UTC，生成时随机 1-24h） */
    private LocalDateTime deliverAfter;

    /** 拆信时间（UTC） */
    private LocalDateTime readAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime deliveredAt;
}
