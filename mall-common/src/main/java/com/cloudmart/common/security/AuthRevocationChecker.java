package com.cloudmart.common.security;

/**
 * 令牌撤销状态检查器（SEC-01）：直连业务服务与网关一致地校验会话账本与主体
 * 认证状态版本，使登出/禁用/改密/权限变更后的旧令牌秒级失效，而不是只依赖
 * 网关一道防线。
 *
 * <p>检查语义（与 mall-auth {@code AuthSessionService} 的 Redis 权威记录对齐）：</p>
 * <ul>
 *   <li>会话记录 {@code auth:session_valid:{sid}} 不存在 → 已撤销（登出/踢出）；</li>
 *   <li>会话记录版本 ≠ 令牌 authVersion → 已撤销；</li>
 *   <li>主体当前版本 {@code auth:auth_version:{type}:{id}} ≠ 令牌 authVersion
 *       → 已撤销（禁用/改密/权限变更递增版本）；</li>
 *   <li>以上全部一致 → 仍有效。</li>
 * </ul>
 *
 * <p>实现约定：状态无法确认（如 Redis 故障）时抛出 {@link AuthStateException}，
 * 由调用方 fail-closed 拒绝——禁止把"查不到"解释为"有效"。</p>
 */
public interface AuthRevocationChecker {

    /**
     * @param subjectType 身份域（user/admin，与令牌 scope/subjectType 声明同值）
     * @param subjectId   令牌主体（sub）
     * @param sid         会话标识
     * @param authVersion 令牌携带的认证状态版本
     * @return true 令牌未被撤销；false 已撤销
     * @throws AuthStateException 撤销状态不可确认（调用方 fail-closed）
     */
    boolean isActive(String subjectType, String subjectId, String sid, long authVersion);
}
