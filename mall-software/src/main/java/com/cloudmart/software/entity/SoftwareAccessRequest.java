package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 受限来源访问申请（SF04）：申请 → 授权员人工核对 → 批准（回写 grant_id）→ resolve。
 * 每用户每软件最多一条 PENDING（生成列唯一键）；审批不得传任意 userId 替换申请人。
 */
@Getter
@Setter
@TableName("software_access_request")
public class SoftwareAccessRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long applicantId;

    private String status;

    private String message;

    private String evidenceAssetIds;

    private String externalReference;

    private Long grantId;

    private Long handledBy;

    private LocalDateTime handledAt;

    private String reason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
