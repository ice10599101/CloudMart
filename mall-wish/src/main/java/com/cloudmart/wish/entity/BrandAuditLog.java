package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 品牌与许愿池审计日志（WISH-03）：审批/池创建/更新/发布/结束/结算重试留痕。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_brand_audit_log")
public class BrandAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long brandId;

    private Long poolId;

    private Long adminUserId;

    /** APPROVE/REJECT/POOL_CREATE/POOL_UPDATE/POOL_PUBLISH/POOL_END/SETTLE_RETRY */
    private String action;

    private String detail;

    private LocalDateTime createdAt;
}
