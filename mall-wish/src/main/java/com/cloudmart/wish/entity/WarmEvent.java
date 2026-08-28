package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.AuditStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 温暖事件（Sprint 3.2，城市幸福地图 UGC：小店老板送咖啡等温暖瞬间）。
 *
 * <p>坐标仅存 geohash7（约 153m 网格），city_code 为 geohash4 城市代理；
 * DFA 命中敏感词 → AUTO_HIDDEN（is_visible=false），未命中 → PENDING
 * 先发后审。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_warm_event")
public class WarmEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 发布者用户 ID */
    private Long userId;

    /** 事件标题（≤60 字） */
    private String title;

    /** 事件内容（≤500 字） */
    private String content;

    /** geohash7 模糊化坐标（约 153m 网格，无原始坐标） */
    private String geohash;

    /** 城市代理（geohash4 前缀） */
    private String cityCode;

    /** 审核状态 */
    private AuditStatus auditStatus;

    /** 是否可见 */
    private Boolean isVisible;

    /** 软删除时间 */
    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
