package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 管理端标签新增/更新（方案 7.5/10.5）：标签仅由后台维护，
 * 作者只能从启用标签中选择；删除即禁用，不做物理删除。
 */
@Getter
@Setter
public class AdminTagSaveRequest {

    /** 标签名（2~20 字） */
    private String name;

    /** 启用（空=true；false=禁用） */
    private Boolean enabled;
}
