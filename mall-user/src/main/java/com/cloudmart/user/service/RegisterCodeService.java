package com.cloudmart.user.service;

/**
 * 注册邮箱验证码服务：发送 / 校验（复用平台级邮件通道 cloudmart.mail.*）。
 *
 * <p>验证码与注销发码（mall-wish B20）同一套安全基线：6 位数字、
 * Redis 仅存 SHA-256 哈希（TTL 5 分钟）、一次性使用；并额外做
 * 发送频控（60 秒冷却 + 每小时上限）防邮件轰炸。</p>
 */
public interface RegisterCodeService {

    /**
     * 向指定邮箱发送注册验证码。
     *
     * <p>邮箱已注册、触发频控时抛业务异常；邮件通道未配置或发送失败时
     * 返回 sent=false + 提示（不假成功，与注销发码语义一致）。</p>
     *
     * @param rawEmail 用户输入邮箱（大小写不敏感，内部统一小写处理）
     * @return 发送结果；echoCode 仅开发/测试回显模式返回
     */
    SendCodeResult sendCode(String rawEmail);

    /**
     * 校验并消费验证码（一次性：校验通过立即删除）。
     *
     * @throws com.cloudmart.common.exception.BusinessException
     *         格式错误或已过期 → USER_REGISTER_CODE_INVALID
     */
    void verifyAndConsume(String rawEmail, String code);

    /**
     * @param sent     验证码是否真实下发
     * @param echoCode echo-code 回显模式的验证码（生产为 null）
     * @param message  sent=false 时的可读原因
     */
    record SendCodeResult(boolean sent, String echoCode, String message) {
    }
}
