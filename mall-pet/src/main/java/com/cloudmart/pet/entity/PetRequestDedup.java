package com.cloudmart.pet.entity;

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
 * 请求级幂等去重（W01）：uk(user,endpoint,requestKey)；同键不同内容 409，同键重试返回原结果。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_request_dedup")
public class PetRequestDedup {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 规范化逻辑操作码 */
    private String endpointKey;

    /** 客户端请求键（16..128 ASCII） */
    private String requestKey;

    /** 规范请求摘要 SHA-256 */
    private String payloadHash;

    /** 业务单 ID */
    private Long bizOrderId;

    /** 状态：PROCESSING/COMPLETED/FAILED */
    private String status;

    /** 租约持有者（P02：实例+线程标识；接管时轮换，旧执行者失效） */
    private String leaseOwner;

    /** 租约到期时间（UTC；到期前同键返回处理中，到期后可被 CAS 接管） */
    private LocalDateTime leaseUntil;

    /** 乐观版本（P02：接管/终态 CAS 推进，防旧执行者覆盖新执行者） */
    private Long version;

    /** 终态响应快照 JSON */
    private String responseJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}