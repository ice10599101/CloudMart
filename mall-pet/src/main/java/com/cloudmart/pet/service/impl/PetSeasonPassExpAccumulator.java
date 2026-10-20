package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.entity.PetSeason;
import com.cloudmart.pet.entity.PetSeasonPass;
import com.cloudmart.pet.entity.PetSeasonPassExpFact;
import com.cloudmart.pet.repository.PetSeasonMapper;
import com.cloudmart.pet.repository.PetSeasonPassExpFactMapper;
import com.cloudmart.pet.repository.PetSeasonPassMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * T17：通行证经验原子累计器（独立 Spring Bean）。
 *
 * <p>修复「事实写入但累计丢失」：原 addExp 在 PetSeasonPassServiceImpl 内以普通方法
 * 完成「插事实 → 累加」，异常被吞；若事实插入提交、累计失败，重放命中
 * {@code uk_fact} 去重直接返回，投影永远少经验。此处把「插来源事实 + 初始化/锁定
 * 通行证 + 累加」放进同一 {@code REQUIRES_NEW} 事务——任一环节失败整体回滚（事实与
 * 累计都不落库），重放时重新插入事实并累加，不再出现中间态。自调用不依赖
 * {@code @Transactional} 代理生效（本 Bean 是独立 Bean，由服务注入调用）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PetSeasonPassExpAccumulator {

    private static final String RULE_VERSION = "V1";

    private final PetSeasonMapper seasonMapper;
    private final PetSeasonPassMapper passMapper;
    private final PetSeasonPassExpFactMapper expFactMapper;
    /** T20：业务日与赛季窗口统一经 PetClock（UTC 存储 + 业务时区归属） */
    private final PetClock clock;

    /**
     * 原子累计：插事实（uk 去重）→ 初始化/锁定通行证 → 累加，同一事务。
     *
     * @return true=已计入（含幂等命中）；false=无进行中赛季（无可归属赛季，不记录）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean accumulate(Long userId, int exp, String source, String sourceBizId) {
        PetSeason season = activeSeason();
        if (season == null) {
            return false;
        }
        PetSeasonPassExpFact fact = new PetSeasonPassExpFact();
        fact.setSeasonId(season.getId());
        fact.setUserId(userId);
        fact.setSource(source);
        fact.setSourceBizId(sourceBizId);
        fact.setExp(exp);
        fact.setBusinessDate(clock.businessDate());
        fact.setRuleVersion(RULE_VERSION);
        try {
            expFactMapper.insert(fact);
        } catch (DuplicateKeyException counted) {
            // 同一来源事实已计入（幂等），不重复累加
            return true;
        }
        PetSeasonPass pass = requirePass(season.getId(), userId);
        passMapper.update(null, new LambdaUpdateWrapper<PetSeasonPass>()
                .eq(PetSeasonPass::getId, pass.getId())
                .setSql("pass_exp = pass_exp + " + exp));
        return true;
    }

    /**
     * T17 校验：按经验事实重算某赛季某用户累计，返回（事实累计, 当前投影）。
     * 只读校验，不自动修正累计数——发现漂移由运维核实后按事实重建，禁止人工加数。
     */
    public long expSumFromFacts(Long seasonId, Long userId) {
        return expFactMapper.selectList(new LambdaQueryWrapper<PetSeasonPassExpFact>()
                        .eq(PetSeasonPassExpFact::getSeasonId, seasonId)
                        .eq(PetSeasonPassExpFact::getUserId, userId))
                .stream()
                .mapToLong(PetSeasonPassExpFact::getExp)
                .sum();
    }

    /** 进行中赛季：ACTIVE 且在起止窗口内（与主服务口径一致） */
    private PetSeason activeSeason() {
        LocalDateTime now = clock.nowUtc();
        return seasonMapper.selectOne(new LambdaQueryWrapper<PetSeason>()
                .eq(PetSeason::getStatus, "ACTIVE")
                .and(w -> w.isNull(PetSeason::getStartsAt).or().le(PetSeason::getStartsAt, now))
                .and(w -> w.isNull(PetSeason::getEndsAt).or().ge(PetSeason::getEndsAt, now))
                .last("LIMIT 1"));
    }

    /** 懒创建通行证行（uk 并发兜底后回查），与主服务 requirePass 同口径 */
    private PetSeasonPass requirePass(Long seasonId, Long userId) {
        PetSeasonPass pass = passMapper.selectOne(new LambdaQueryWrapper<PetSeasonPass>()
                .eq(PetSeasonPass::getSeasonId, seasonId)
                .eq(PetSeasonPass::getUserId, userId));
        if (pass != null) {
            return pass;
        }
        PetSeasonPass created = new PetSeasonPass();
        created.setSeasonId(seasonId);
        created.setUserId(userId);
        created.setPassExp(0);
        created.setClaimedTiers("[]");
        created.setVersion(0);
        try {
            passMapper.insert(created);
            return created;
        } catch (DuplicateKeyException concurrent) {
            return passMapper.selectOne(new LambdaQueryWrapper<PetSeasonPass>()
                    .eq(PetSeasonPass::getSeasonId, seasonId)
                    .eq(PetSeasonPass::getUserId, userId));
        }
    }
}
