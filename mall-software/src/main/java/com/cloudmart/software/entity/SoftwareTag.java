package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 软件标签（方案 6.7）：独立于社区 tags 语义；每快照 ≤5 由服务端校验 */
@Getter
@Setter
@TableName("software_tag")
public class SoftwareTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名（2~20 字） */
    private String name;

    /** 规范化标签（去重键） */
    private String normalizedName;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
