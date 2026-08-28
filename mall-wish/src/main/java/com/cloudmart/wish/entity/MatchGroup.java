package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.MatchGroupStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 同愿小组（Sprint 2.6，文档 1.2 ⑧ wish_match_group）。
 *
 * <p>memberCount 为事务内维护的 ACTIVE 成员计数（非生成列）：
 * 加组走 CAS UPDATE（member_count &lt; max_members AND status='OPEN'）
 * 防并发超卖名额，退组反向递减。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_match_group")
public class MatchGroup {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 组主题关键词（来自心愿标签/目标） */
    private String keyword;

    /** 小组容量（2-4 人） */
    private Integer maxMembers;

    /** 关联心愿 ID（可空，自由建组） */
    private Long wishId;

    /** 组长用户 ID（创建者，退出自动转让） */
    private Long leaderId;

    /** 当前 ACTIVE 成员数（事务内维护） */
    private Integer memberCount;

    /** 同城代理（创建人活跃公开心愿 geohash 前缀 4，可空） */
    private String cityCode;

    /** 状态 */
    private MatchGroupStatus status;

    /** 关闭时间（UTC，解散或无人时写入） */
    private LocalDateTime closedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
