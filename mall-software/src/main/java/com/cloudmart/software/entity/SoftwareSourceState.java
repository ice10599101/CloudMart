package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.software.enums.SourceAvailability;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 来源核实/禁用状态（方案 6.6）：独立于不可变已审快照，键 (software_id, source_key)；
 * DISABLED 无条件禁用，其余结论按指纹匹配应用；复制快照保持禁用继承。
 */
@Getter
@Setter
@TableName("software_source_state")
public class SoftwareSourceState {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private String sourceKey;

    private SourceAvailability availability;

    /** 核实时的内容指纹（与当前不一致则结论不生效，展示 UNKNOWN） */
    private String checkedFingerprint;

    private LocalDateTime checkedAt;

    private Long checkedBy;

    private String reason;

    private Integer lockVersion;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
