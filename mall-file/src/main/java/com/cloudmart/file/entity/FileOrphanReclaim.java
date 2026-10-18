package com.cloudmart.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * T27：文件孤儿实物回收义务（FILE-01 补充）。
 *
 * <p>上传失败清理实物也失败时，把残留对象登记为可恢复的回收义务，而不是只记一条日志。
 * 回收只针对本表登记的 storageKey（白名单），绝不接受调用方传入的任意路径。</p>
 */
@Data
@TableName("file_orphan_reclaim")
public class FileOrphanReclaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 孤儿实物存储键（白名单回收范围） */
    private String storageKey;

    /** 产生原因（如 UPLOAD_CLEANUP_FAILED） */
    private String reason;

    /** PENDING / RECLAIMED / FAILED */
    private String status;

    private Integer attempts;

    private LocalDateTime nextRetryAt;

    private String error;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
