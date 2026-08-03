package com.cloudmart.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("admin_user")
public class AdminUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deptId;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private Integer sex;

    private String avatar;

    private String password;

    private Integer status;

    private String loginIp;

    private LocalDateTime loginDate;

    private LocalDateTime pwdUpdateDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private String remark;

    @TableLogic
    private LocalDateTime deletedAt;
}
