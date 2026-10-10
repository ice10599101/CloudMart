package com.cloudmart.notification.channel;

import java.util.Map;

/**
 * N-1 订阅消息通道抽象：站内通知之外的第二触达面（微信订阅消息/短信/推送）。
 *
 * <p>实现方按配置装配：{@code notification.subscribe-message.provider=log}（默认，
 * 仅打印日志便于联调）或 {@code wechat}（微信小程序订阅消息，需注入模板 ID 与
 * access_token 基础设施后实现）。发送失败由实现方自行降级，不阻断站内通知主链路。</p>
 */
public interface SubscribeMessageChannel {

    /**
     * 发送订阅消息。
     *
     * @param userId    用户 ID
     * @param templateKey 业务模板键（如 ANNIVERSARY / SHIPPED；由实现方映射到平台模板 ID）
     * @param data      模板字段（键值对）
     * @return 是否发送成功（log 通道恒 true）
     */
    boolean send(Long userId, String templateKey, Map<String, String> data);

    /**
     * N-1：Taro.login code → openid（code2session），供授权建档；
     * 仅 wechat 实现支持，log 实现抛不支持。
     */
    default String openidByCode(String jsCode) {
        throw new UnsupportedOperationException("当前通道不支持 code2session");
    }
}
