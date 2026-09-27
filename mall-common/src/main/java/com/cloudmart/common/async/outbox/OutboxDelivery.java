package com.cloudmart.common.async.outbox;

import com.cloudmart.common.async.EventEnvelope;

/**
 * Outbox 投递适配器（ASYNC-01）：由接入模块实现，将事件发送到本模块的
 * MQ 目的地（RocketMQ topic:tag）。
 *
 * <p>约定：</p>
 * <ul>
 *   <li>实现只做"尽力投递一次"——失败抛异常即可，重试与死信由 Outbox 统一管理；</li>
 *   <li>投递采用 syncSend 阻塞式确认（发送结果确定性优先于吞吐）；</li>
 *   <li>消费端必须配合 Inbox 幂等消费（重发被容忍）。</li>
 * </ul>
 */
public interface OutboxDelivery {

    /**
     * @return 目的地描述（日志/工作台展示用，如 payment-events:PAYMENT_RESULT）
     */
    String destination(EventEnvelope envelope);

    /**
     * 投递事件；失败抛出任意异常，由 Outbox 记录退避重试。
     */
    void deliver(EventEnvelope envelope);
}
