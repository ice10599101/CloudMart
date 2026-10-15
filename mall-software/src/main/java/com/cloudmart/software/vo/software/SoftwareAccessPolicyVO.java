package com.cloudmart.software.vo.software;

import java.util.List;

/**
 * 当前线上查看条件摘要（方案 7.2）：仅展示条件本身；
 * 无用户授权记录、无付款备注、无源 URL/提取码——个人状态走登录 access-state 接口。
 */
public record SoftwareAccessPolicyVO(
        Long softwareId,
        String accessMode,
        Integer requiredLevel,
        String accessNote,
        /** 仅付款模式返回启用中的公开联系入口 */
        List<ContactVO> contacts) {

    /** 公开联系入口（管理员维护；停用不出现） */
    public record ContactVO(Long id, String name, String contactType, String contactValue) {
    }
}
