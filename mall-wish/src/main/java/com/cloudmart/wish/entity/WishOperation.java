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
 * 通用写操作持久幂等记录（B04，任务书 §6.2）。
 *
 * <p>唯一作用域 {@code (actor_type, actor_id, operation_type, request_key)}：
 * 去重行与领域写操作同事务提交——"操作已生效"与"余额/库存已变化"原子成立。
 * 仅 COMPLETED 结果落库：失败事务整体回滚，行不存在即结果未知，调用方可按原键重试。
 * 同键同摘要重放已提交结果；同键异摘要 409 IDEMPOTENCY_KEY_REUSED。
 * 旧路径 {@link WishPetOperation} 保留兼容，禁止清表后重放旧请求。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_operation")
public class WishOperation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作者类型：USER / ADMIN / JOB / SERVICE */
    private String actorType;

    /** 数值型操作者 ID；服务主体填 0 */
    private Long actorId;

    /** 非数值主体标识（serviceId，如 mall-job） */
    private String actorRef;

    /** 操作类型：ASSET_EXCHANGE / GIFT_SEND / STARLIGHT_DECAY 等 */
    private String operationType;

    /** 请求键（客户端幂等键或业务唯一键，ascii） */
    private String requestKey;

    /** 请求摘要 SHA-256（操作类型+目标+业务参数） */
    private String requestHash;

    /** 目标类型（资产/心愿/礼物） */
    private String targetType;

    private Long targetId;

    /** 仅 COMPLETED 对外可见（PROCESSING 只在未提交事务中存在） */
    private String status;

    /** 已完成结果 JSON（重放依据） */
    private String responseJson;

    private String errorCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
}
