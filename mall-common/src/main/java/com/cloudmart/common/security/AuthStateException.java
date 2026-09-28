package com.cloudmart.common.security;

/**
 * 撤销状态校验本身不可用（SEC-01）：Redis 故障等导致无法确认令牌是否已被撤销。
 *
 * <p>调用方必须 fail-closed——拒绝建立身份，而不是当作"未撤销"放行。
 * 撤销状态查不到时宁可拒绝，绝不允许未校验撤销的令牌通过。</p>
 */
public class AuthStateException extends RuntimeException {

    public AuthStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
