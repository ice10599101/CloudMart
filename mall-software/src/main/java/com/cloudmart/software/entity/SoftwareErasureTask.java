package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 软件域账号注销任务（方案 11.3）：每用户唯一；分阶段游标与本地清理同事务，
 * DONE 后重复调用返回成功；未完成返回可重试错误，注销编排不得推进文件擦除。
 */
@Data
@TableName("software_erasure_task")
public class SoftwareErasureTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** PENDING/RUNNING/DONE/FAILED */
    private String status;

    /** 当前阶段标识（阶段完成即推进，供续跑与排查） */
    private String phase;

    /** 阶段内游标（大批量分批处理） */
    private Long cursorId;

    private Integer attempts;

    private LocalDateTime nextRetryAt;

    private String leaseOwner;

    private LocalDateTime leaseUntil;

    private Integer leaseVersion;

    private String lastError;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
