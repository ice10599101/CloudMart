package com.cloudmart.wish.service;

/**
 * 心愿数据擦除 worker（W03/LC15）：mall-user 为注销申请/取消/状态查询的唯一权威，
 * 本服务仅保留被编排调用的幂等擦除步骤——wish 独立申请/冷静期调度/验证码入口已删除。
 */
public interface AccountDeletionService {

    /**
     * 心愿数据幂等擦除（软删保留审计；PRIVATE/TREE_HOLE 全量）。
     * 由 mall-user 注销编排经内部端点（服务令牌）调用；重复调用返回原结果。
     */
    boolean eraseUserData(Long userId);
}
