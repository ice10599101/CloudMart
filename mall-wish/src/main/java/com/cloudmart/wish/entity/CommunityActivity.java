package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.ActivityStatus;
import com.cloudmart.wish.enums.ActivityType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 社区活动配置（Sprint 3.5，文档 1.2 ㉜ wish_activity）。
 *
 * <p>配置表化：新增活动仅插入配置行，前端自动展示活动入口；
 * condition/reward 为 JSON 字符串（ActivityConditionParser 解析）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_activity")
public class CommunityActivity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 活动类型 */
    private ActivityType type;

    /** 活动标题 */
    private String title;

    /** 活动描述/规则说明 */
    private String description;

    /** 封面图（节日氛围装饰） */
    private String coverImage;

    /** 触发条件 JSON（如 {"type":"PROGRESS_COUNTER","threshold":100}） */
    private String conditionJson;

    /** 奖励配置 JSON（如 {"starlight":100,"badgeCode":"COLLABORATOR"}） */
    private String rewardJson;

    /** 城市代理（geohash4，城市活动专用） */
    private String cityCode;

    /** 状态机 */
    private ActivityStatus status;

    /** 展示开始（UTC） */
    private LocalDateTime validFrom;

    /** 展示结束（UTC，到期入口消失但详情仍可访问） */
    private LocalDateTime validTo;

    /** 进度计数（Redis INCR 周期回写镜像） */
    private Long progressCounter;

    /** 创建管理员 */
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
