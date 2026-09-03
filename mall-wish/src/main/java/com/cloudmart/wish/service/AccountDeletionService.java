package com.cloudmart.wish.service;

import com.cloudmart.wish.entity.WishAccountDeletion;

/**
 * 账号注销宽限期服务（合规 34.2 / API 2.13，四AB A1）。
 *
 * <p>流程：发送 6 位验证码（Redis 存 SHA-256 哈希，5 分钟有效）→
 * 凭验证码申请注销（30 天宽限期）→ 宽限期内可撤回 → 到期由定时任务
 * 执行数据清理（心愿逻辑删除等；mall-user 账号禁用为跨服务联动）。</p>
 */
public interface AccountDeletionService {

    /** 发送注销验证码（生成 6 位码，Redis 存哈希 TTL 5min；回显由 echo-code 配置控制） */
    String sendDeletionCode(Long userId);

    /** 申请注销（验证码校验；已有 PENDING 任务 409） */
    WishAccountDeletion apply(Long userId, String confirmCode, String reason);

    /** 撤回注销（PENDING → CANCELED；EXECUTED 409 不可撤回） */
    WishAccountDeletion cancel(Long userId);

    /** 当前注销状态（无记录返回 null） */
    WishAccountDeletion getStatus(Long userId);

    /** 执行到期的注销（execute_after <= now 的 PENDING；数据清理 + EXECUTED） */
    int executeDue();
}
