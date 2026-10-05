package com.cloudmart.community.service;

/**
 * 账号数据擦除服务（T06 注销编排，mall-user 经 Feign 调用）。
 */
public interface AccountErasureService {

    /** 擦除用户社区内容（帖子/评论软删+去标识化）；幂等 */
    boolean eraseUserData(Long userId);
}
