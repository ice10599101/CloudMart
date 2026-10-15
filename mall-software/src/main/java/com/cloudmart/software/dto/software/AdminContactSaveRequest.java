package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/** 联系入口新增/更新（方案 6.14）：内容管理员维护，拒绝秘密信息和危险协议 */
@Getter
@Setter
public class AdminContactSaveRequest {

    private String idempotencyKey;

    /** null=新增；携带=更新（引用中仅停用，不物理删除） */
    private Long id;

    private String name;

    /** SITE_USER/TEXT/HTTPS_URL */
    private String contactType;

    private String contactValue;

    private Boolean enabled;

    private Integer sortOrder;

    private String reason;
}
