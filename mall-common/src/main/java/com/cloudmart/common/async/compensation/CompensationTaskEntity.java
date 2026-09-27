package com.cloudmart.common.async.compensation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 补偿任务实体（ASYNC-01）：跨服务动作失败的持久化待办（如库存确认/释放、退券）。
 *
 * <p>字段满足方案要求：持久 taskId、业务单号、动作、状态、尝试次数、下次时间、
 * 最后错误、锁租期；实例宕机后其他实例按租约接管。</p>
 */
@TableName("compensation_task")
public class CompensationTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务幂等键（如 stock-confirm:{orderId}:{skuId}） */
    @TableField("task_id")
    private String taskId;

    private String action;

    @TableField("aggregate_id")
    private String aggregateId;

    private String payload;

    private String status;

    private Integer attempts;

    @TableField("next_retry_at")
    private LocalDateTime nextRetryAt;

    @TableField("last_error")
    private String lastError;

    @TableField("locked_by")
    private String lockedBy;

    @TableField("locked_at")
    private LocalDateTime lockedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
