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

/** 配置历史版本（B21）：发布快照/回退/审计。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_config_version")
public class PetConfigVersion {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String configType;
    private Long configId;
    private Integer version;
    private String snapshot;
    private String operation;
    private String operator;;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
