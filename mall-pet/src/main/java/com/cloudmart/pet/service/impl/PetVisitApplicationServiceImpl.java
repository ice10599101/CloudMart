package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.entity.PetVisitFact;
import com.cloudmart.pet.repository.PetVisitFactMapper;
import com.cloudmart.pet.service.impl.PetQuotaService;
import com.cloudmart.pet.service.PetUserGuardService;
import com.cloudmart.pet.service.PetVisitApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 拜访统一应用服务实现（B01/BE-06）。
 *
 * <p>事务顺序（加入调用方事务）：锁访问者 user guard → 插入拜访事实（唯一键裁决）→
 * 收益额度裁决（数据库条件 UPDATE）。同一访问者的并发拜访在 guard 行上串行化，
 * 不存在"两个入口同时各记一次"的窗口。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PetVisitApplicationServiceImpl implements PetVisitApplicationService {

    private final PetUserGuardService guardService;
    private final PetVisitFactMapper factMapper;
    private final PetQuotaService quotaService;
    private final PetClock petClock;
    private final com.cloudmart.pet.config.PetProperties properties;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public VisitGrant recordVisit(Long visitorUserId, Long ownerUserId,
                                  Long visitorPetId, Long ownerPetId, VisitSource source) {
        // 1) 用户行锁：同一访问者的多入口/多端并发在此串行化
        guardService.lockGuard(visitorUserId);
        LocalDate businessDate = petClock.businessDate();

        // 2) 事实唯一键：同主人同业务日至多一次（重复拜访 = 冷却拒绝）
        PetVisitFact fact = new PetVisitFact();
        fact.setVisitorUserId(visitorUserId);
        fact.setOwnerUserId(ownerUserId);
        fact.setVisitorPetId(visitorPetId);
        fact.setOwnerPetId(ownerPetId);
        fact.setSource(source.name());
        fact.setBusinessDate(businessDate);
        fact.setRewardGranted(0);
        try {
            factMapper.insert(fact);
        } catch (DuplicateKeyException duplicate) {
            return new VisitGrant(false, false);
        }

        // 3) 收益额度（数据库权威）：共享收益上限 + 好友入口更严上限。
        // R14：分别保存本次各额度占用结果，只回退本次成功占用的——原实现短路失败时
        // 也无条件 release FRIEND，把历史 used 减 1（免费收益漏洞）
        boolean rewardGranted;
        if (source == VisitSource.FRIEND) {
            boolean friendConsumed = quotaService.tryConsume(visitorUserId,
                    PetQuotaService.QuotaType.FRIEND_VISIT_REWARD, 0, properties.getFriend().getDailyVisitLimit());
            boolean sharedConsumed = friendConsumed && quotaService.tryConsume(visitorUserId,
                    PetQuotaService.QuotaType.VISIT_REWARD, 0, properties.getHome().getDailyVisitLimit());
            rewardGranted = friendConsumed && sharedConsumed;
            if (friendConsumed && !sharedConsumed) {
                // 第二额度失败：仅回退本次已占用的友好额度
                quotaService.release(visitorUserId, PetQuotaService.QuotaType.FRIEND_VISIT_REWARD, 0);
            }
        } else {
            rewardGranted = quotaService.tryConsume(visitorUserId, PetQuotaService.QuotaType.VISIT_REWARD,
                    0, properties.getHome().getDailyVisitLimit());
        }
        if (rewardGranted != (fact.getRewardGranted() == 1)) {
            fact.setRewardGranted(rewardGranted ? 1 : 0);
            factMapper.updateById(fact);
        }
        log.info("拜访事实记录, visitor={}, owner={}, source={}, businessDate={}, reward={}",
                visitorUserId, ownerUserId, source, businessDate, rewardGranted);
        return new VisitGrant(true, rewardGranted);
    }

    /** @return 访问者今日是否已拜访过该主人（展示用，读库不读 Redis） */
    @Override
    public boolean visitedToday(Long visitorUserId, Long ownerUserId) {
        return factMapper.selectCount(new LambdaQueryWrapper<PetVisitFact>()
                .eq(PetVisitFact::getVisitorUserId, visitorUserId)
                .eq(PetVisitFact::getOwnerUserId, ownerUserId)
                .eq(PetVisitFact::getBusinessDate, petClock.businessDate())) > 0;
    }
}
