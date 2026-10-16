package com.cloudmart.wish.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 品牌许愿池开关（WISH-03）。
 *
 * <p>审计（P0）：池参与/自动发奖存在"结算无唯一奖励事实、无非幂等原子认领、
 * 两实例同时扫描可能重复入账"的风险。在"唯一奖励义务 + 池级 CAS 结算"修复
 * 完成并通过并发验收前，默认关闭——参加返回明确维护态（503
 * WISH_BRAND_POOL_MAINTENANCE），定时发奖跳过不执行。</p>
 *
 * <p>关闭仅影响品牌许愿池的参加与发奖，不影响其他心愿/收藏/工坊能力；
 * 后台池管理（创建/编辑/结束）不受影响。修复上线后按需打开。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "wish.brand-pool")
public class WishBrandPoolProperties {

    /** 是否开放品牌池参加与自动发奖（默认 false：WISH-03 修复前的风险缓解） */
    private boolean enabled = false;
}
