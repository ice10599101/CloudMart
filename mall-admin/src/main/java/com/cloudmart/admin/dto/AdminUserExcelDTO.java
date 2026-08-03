package com.cloudmart.admin.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserExcelDTO {

    @ExcelProperty("用户名")
    private String username;

    @ExcelProperty("昵称")
    private String nickname;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("性别")
    private Integer sex;

    @ExcelProperty("部门ID")
    private Long deptId;

    @ExcelProperty("状态")
    private Integer status;

    @ExcelProperty("备注")
    private String remark;
}
