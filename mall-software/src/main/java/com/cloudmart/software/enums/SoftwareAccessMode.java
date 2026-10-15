package com.cloudmart.software.enums;

/**
 * 链接查看条件（方案 5.4）：整款软件单选，适用于网盘、磁力及种子外链全部来源。
 * 三种模式互斥：付款授权不提升社区等级，也不绕过 COMMUNITY_LEVEL。
 */
public enum SoftwareAccessMode {
    /** 注册用户：登录且账号状态正常即放行 */
    REGISTERED,
    /** 社区等级：登录且现有社区等级 ≥ minCommunityLevel */
    COMMUNITY_LEVEL,
    /** 联系管理员付款查看：登录且存在有效人工授权记录 */
    ADMIN_PAID
}
