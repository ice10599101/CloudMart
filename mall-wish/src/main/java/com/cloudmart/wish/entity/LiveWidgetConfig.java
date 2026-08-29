package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.WidgetPosition;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 直播心愿挂件配置（Sprint 3.4，文档 1.2 ㊱）。
 *
 * <p>主播维度唯一（uk_widget_streamer）；挂件数据本身实时聚合自
 * 心愿/进度/统计，无独立数据表；全局降级开关复用灰度配置
 * feature_key=wish_live_widget（比例 0=隐藏/100=展示）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_live_widget_config")
public class LiveWidgetConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 主播用户 ID（唯一） */
    private Long streamerId;

    /** 挂件位置 */
    private WidgetPosition position;

    /** 样式配置 JSON（如 {"transparent":true,"accent":"#ffd700"}） */
    private String styleConfig;

    /** 该主播挂件是否展示 */
    private Boolean isVisible;

    /** 最后修改人（管理后台用户 ID） */
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
