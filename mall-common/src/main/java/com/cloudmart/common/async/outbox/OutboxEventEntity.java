package com.cloudmart.common.async.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Outbox 事件持久化实体（ASYNC-01）：与业务写同库同事务插入，由后台批量投递。
 *
 * <p>状态机：PENDING → SENDING → SENT；投递失败回 FAILED（带退避）；
 * 超过最大尝试次数转 DEAD_LETTER（转人工/工作台）。SENDING 且锁过期的行
 * 可被其他实例接管（实例宕机后的可靠投递）。</p>
 */
@TableName("outbox_event")
public class OutboxEventEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("event_id")
    private String eventId;

    @TableField("event_type")
    private String eventType;

    @TableField("schema_version")
    private Integer schemaVersion;

    @TableField("aggregate_id")
    private String aggregateId;

    @TableField("aggregate_version")
    private Long aggregateVersion;

    @TableField("request_id")
    private String requestId;

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

    @TableField("sent_at")
    private LocalDateTime sentAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public Long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
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
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
