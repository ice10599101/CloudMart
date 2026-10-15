package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 付款查看联系入口（方案 5.4/6.14）：后台统一配置；发布者只能选择已启用入口；
 * 引用中仅允许停用，不物理删除。
 */
@Getter
@Setter
@TableName("software_access_contact")
public class SoftwareAccessContact {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** SITE_USER/TEXT/HTTPS_URL */
    private String contactType;

    /** 联系内容（管理员维护；拒绝秘密信息和危险协议） */
    private String contactValue;

    private Boolean enabled;

    private Integer sortOrder;

    private Long updatedBy;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
