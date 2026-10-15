package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 操作幂等事实（方案 6.10/7.3）：创建软件/克隆工作快照/发布提交/审核切换/创建评论等；
 * 业务变更与结果记录同事务提交；同键同请求返回原结果，同键异体 409。
 */
@Getter
@Setter
@TableName("software_operation")
public class SoftwareOperation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** USER/ADMIN（区分用户和管理员 ID 命名空间） */
    private String actorType;

    private Long actorId;

    /** 客户端 Idempotency-Key */
    private String operationKey;

    private String operationType;

    /** 请求体规范化摘要（同键异体 409 判定） */
    private String requestHash;

    private Long softwareId;

    /** 首次执行结果（只存 ID/版本号/状态，不存源链接） */
    private String resultJson;

    private LocalDateTime createdAt;
}
