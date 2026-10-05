package com.cloudmart.notification.service;

/**
 * 账号数据擦除服务（T06 注销编排，mall-user 经 Feign 调用）。
 */
public interface AccountErasureService {

    /** 擦除用户通知/会话/消息等个人数据（物理删除）；幂等 */
    boolean eraseUserData(Long userId);
}
