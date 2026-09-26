package com.cloudmart.wish.entity;

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
 * 漂流瓶打捞流水：每次成功捞起插入一条，扔回海里不删除。
 *
 * <p>每日打捞配额（20 次/天）与看板打捞趋势以流水为准——若按瓶子表的
 * picker_user_id 统计，扔回海里清空捞起人后配额会被错误回退。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_drift_bottle_fish_log")
public class DriftBottleFishLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 漂流瓶 ID */
    private Long bottleId;

    /** 打捞人用户 ID */
    private Long userId;

    /** 业务请求标识（宠物代捞按活动生成，B11 幂等重放依据；社区手动打捞为空） */
    private String requestId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
