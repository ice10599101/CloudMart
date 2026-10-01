package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.service.AccountDeletionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 心愿数据擦除 worker（W03/LC15）。
 *
 * <p>mall-user 为全账号注销申请/取消/状态查询的唯一权威（B20 编排）；本服务仅保留
 * 被编排调用的幂等擦除步骤——wish 独立申请/冷静期调度/验证码入口/独立到期扫描
 * 已删除（含旧路由 /my/account-deletion*）。</p>
 *
 * <p>幂等语义：心愿为逻辑删除（保留审计），重复执行无害；每次调用都重新执行软删
 * 以覆盖编排重试窗口内新产生的脏数据。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionServiceImpl implements AccountDeletionService {

    private final WishMapper wishMapper;

    @Override
    @Transactional
    public boolean eraseUserData(Long userId) {
        int deleted = wishMapper.delete(new LambdaQueryWrapper<Wish>()
                .eq(Wish::getUserId, userId));
        log.warn("W03 编排擦除心愿数据 userId={}, deleted={}", userId, deleted);
        return true;
    }
}
