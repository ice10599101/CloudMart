package com.cloudmart.auth.service;

/**
 * 刷新令牌身份域（SEC-02）：普通用户与管理员是两个不可互换的身份域。
 *
 * <p>令牌值首字符即域标记（{@code u:}/{@code a:}）——用户入口只接受 USER 令牌，
 * 管理员入口只接受 ADMIN 令牌；跨域提交在触及任何 Redis 状态前即被拒绝，
 * 原令牌不被消费，不产生任何身份混淆面。</p>
 */
public enum SubjectType {

    USER("u:"),
    ADMIN("a:");

    private final String prefix;

    SubjectType(String prefix) {
        this.prefix = prefix;
    }

    /** 令牌值中的域标记前缀 */
    public String prefix() {
        return prefix;
    }

    /** 主体索引键中的域段 */
    public String segment() {
        return name().toLowerCase();
    }

    /** @return 令牌值对应的身份域；格式非法返回 null */
    public static SubjectType fromToken(String tokenValue) {
        if (tokenValue == null) {
            return null;
        }
        for (SubjectType type : values()) {
            if (tokenValue.startsWith(type.prefix)) {
                return type;
            }
        }
        return null;
    }
}
