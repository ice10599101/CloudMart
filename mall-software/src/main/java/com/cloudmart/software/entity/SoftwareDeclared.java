package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 软件结构化声明（UX04-B）：对比用客观字段。
 * NULL=未提供（对比页显示"未提供"，禁止推断"支持/不支持"）；
 * 编辑推荐结论与客观字段分离，不伪造测试结论。
 */
@Data
@TableName("software_declared")
public class SoftwareDeclared {

    /** 软件ID（一对一主键） */
    @TableId(type = IdType.INPUT)
    private Long softwareId;

    /** 是否开源（NULL=未提供） */
    private Boolean openSource;

    /** 离线可用（NULL=未提供） */
    private Boolean offlineCapable;

    private String declaredNote;

    private Long updatedBy;

    private LocalDateTime updatedAt;
}
