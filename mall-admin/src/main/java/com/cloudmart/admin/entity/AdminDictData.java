package com.cloudmart.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("admin_dict_data")
public class AdminDictData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictType;

    private Integer dictSort;

    private String dictLabel;

    private String dictValue;

    private String cssClass;

    private String listClass;

    private Integer isDefault;

    private Integer status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
