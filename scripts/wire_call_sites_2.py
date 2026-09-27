# -*- coding: utf-8 -*-
"""W02 调用点接入脚本（pass-2，一次性，用后即删）"""
import io
import re

BASE = r'mall-pet/src/main/java/com/cloudmart/pet/service/impl/'


def swap_dependency(fname):
    p = BASE + fname
    s = io.open(p, encoding='utf-8').read()
    assert 'private final PetOperationService operationService;' in s, fname
    s = s.replace('private final PetOperationService operationService;',
                  'private final PetEconomyService economyService;', 1)
    s = s.replace('this.operationService = operationService;',
                  'this.economyService = economyService;', 1)
    m = re.search(r'(\n\s+)PetOperationService operationService\)', s)
    if m:
        s = s.replace(m.group(0), m.group(1) + 'PetEconomyService economyService)', 1)
    else:
        m = re.search(r'(\n\s+)PetOperationService operationService,\n', s)
        assert m, fname
        s = s.replace(m.group(0), m.group(1) + 'PetEconomyService economyService,\n', 1)
    m = re.search(r'^import com\.cloudmart\.pet\.', s, re.M)
    s = s[:m.start()] + 'import com.cloudmart.pet.wallet.PetEconomyService;\n' + s[m.start():]
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('swap', fname)


def edit(fname, pairs):
    p = BASE + fname
    s = io.open(p, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, fname + ' :: ' + repr(old[:80])
        s = s.replace(old, new, 1)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('ok', fname)


# ---------------- PetCareerServiceImpl ----------------
swap_dependency('PetCareerServiceImpl.java')
edit('PetCareerServiceImpl.java', [
    ('''            String operationId = operationService.operationKey("CAREER_CLAIM", activity.getId());
            PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                    operationId, userId, activity.getPetId(), "CAREER_CLAIM", activity.getId(),
                    currencyReward, null);''',
     '''            PetOperationService.WalletSettlement settlement = economyService.earn(
                    userId, activity.getPetId(), "CAREER_CLAIM", activity.getId(),
                    currencyReward, null, activity.getId());'''),
    ('''            String operationId = operationService.requestOperationKey(BIZ_TYPE_PROMOTE, pet.getId(), target.getCode());
            String promoteSnapshot = PetJsonUtils.toJson(Map.of(
                    "careerTo", target.getCode(),
                    "careerFrom", current.getCode()));
            PetOperationService.WalletSettlement settlement = operationService.executeSpend(
                    operationId, userId, pet.getId(), BIZ_TYPE_PROMOTE, pet.getId(), cost, promoteSnapshot);
            if (settlement.isUnknown()) {
                throw operationService.settlementPending();
            }''',
     '''            String promoteSnapshot = PetJsonUtils.toJson(Map.of(
                    "careerTo", target.getCode(),
                    "careerFrom", current.getCode()));
            PetOperationService.WalletSettlement settlement = economyService.spend(
                    userId, pet.getId(), BIZ_TYPE_PROMOTE, pet.getId(), cost, promoteSnapshot,
                    pet.getId(), target.getCode());
            if (settlement.isUnknown()) {
                throw economyService.settlementPending();
            }'''),
])

# ---------------- PetActivityServiceImpl ----------------
swap_dependency('PetActivityServiceImpl.java')
edit('PetActivityServiceImpl.java', [
    ('''        String bizType = "WORK".equals(activity.getActivityType()) ? "CLAIM_WORK" : "CLAIM_STUDY";
        String operationId = operationService.operationKey(bizType, activity.getId());
        PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                operationId, activity.getUserId(), activity.getPetId(), bizType, activity.getId(),
                amount, null);
        if (settlement.isCompleted()) {
            return settlement.credited();
        }
        log.info("活动奖励星光结算中, activityId={}, operationId={}",
                activity.getId(), operationId, settlement.status());
        return null;''',
     '''        String bizType = "WORK".equals(activity.getActivityType()) ? "CLAIM_WORK" : "CLAIM_STUDY";
        PetOperationService.WalletSettlement settlement = economyService.earn(
                activity.getUserId(), activity.getPetId(), bizType, activity.getId(),
                amount, null, activity.getId());
        if (settlement.isCompleted()) {
            return settlement.credited();
        }
        log.info("活动奖励星光结算中, activityId={}, status={}",
                activity.getId(), settlement.status());
        return null;'''),
])

# ---------------- PetBattleServiceImpl ----------------
swap_dependency('PetBattleServiceImpl.java')
edit('PetBattleServiceImpl.java', [
    ('''            String operationId = operationService.operationKey("BATTLE_REWARD", battle.getId(), "attacker");
            PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                    operationId, battle.getAttackerUserId(), battle.getAttackerPetId(),
                    "BATTLE_REWARD", battle.getId(), battle.getCurrencyReward(), null);
            if (!settlement.isCompleted()) {
                log.info("对战奖励星光结算中, battleId={}, side=attacker, operationId={}",
                        battle.getId(), operationId);
            }''',
     '''            PetOperationService.WalletSettlement settlement = economyService.earn(
                    battle.getAttackerUserId(), battle.getAttackerPetId(),
                    "BATTLE_REWARD", battle.getId(), battle.getCurrencyReward(), null,
                    battle.getId(), "attacker");
            if (!settlement.isCompleted()) {
                log.info("对战奖励星光结算中, battleId={}, side=attacker, status={}",
                        battle.getId(), settlement.status());
            }'''),
    ('''                    String operationId = operationService.operationKey("BATTLE_REWARD", battle.getId(), "defender");
                    PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                            operationId, defender.getUserId(), defender.getId(),
                            "BATTLE_REWARD", battle.getId(), battle.getCurrencyReward(), null);
                    if (!settlement.isCompleted()) {
                        log.info("对战奖励星光结算中, battleId={}, side=defender, operationId={}",
                                battle.getId(), operationId);
                    }''',
     '''                    PetOperationService.WalletSettlement settlement = economyService.earn(
                            defender.getUserId(), defender.getId(),
                            "BATTLE_REWARD", battle.getId(), battle.getCurrencyReward(), null,
                            battle.getId(), "defender");
                    if (!settlement.isCompleted()) {
                        log.info("对战奖励星光结算中, battleId={}, side=defender, status={}",
                                battle.getId(), settlement.status());
                    }'''),
])

# ---------------- PetBottleSettlementService ----------------
swap_dependency('PetBottleSettlementService.java')
edit('PetBottleSettlementService.java', [
    ('''                String operationId = operationService.operationKey("BOTTLE_REWARD", activity.getId());
                PetOperationService.WalletSettlement settlement = operationService.executeEarn(
                        operationId, activity.getUserId(), activity.getPetId(),
                        "BOTTLE_REWARD", activity.getId(), RARE_STARLIGHT, null);
                if (!settlement.isCompleted()) {
                    log.info("稀有瓶星光结算中, activityId={}, operationId={}", activity.getId(), operationId);
                }''',
     '''                PetOperationService.WalletSettlement settlement = economyService.earn(
                        activity.getUserId(), activity.getPetId(),
                        "BOTTLE_REWARD", activity.getId(), RARE_STARLIGHT, null, activity.getId());
                if (!settlement.isCompleted()) {
                    log.info("稀有瓶星光结算中, activityId={}, status={}", activity.getId(), settlement.status());
                }'''),
])

print('done-pass-2')
