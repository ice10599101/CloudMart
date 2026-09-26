package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.pet.config.PetClock;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetCompanionDaily;
import com.cloudmart.pet.entity.PetCompanionSession;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetCompanionDailyMapper;
import com.cloudmart.pet.repository.PetCompanionSessionMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.util.PetIntimacyMath;
import com.cloudmart.pet.vo.PetCompanionSessionVO;
import com.cloudmart.pet.vo.PetIntimacyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 亲密度与陪伴实现（三期 + B05）。
 *
 * <p>亲密度来源数值集中在 {@link PetProperties.Intimacy}；{@link #gain} 原子落库
 * （目标列 SQL 自增，多来源并发不丢增量，跨天/升级判定基于写入后实体值）。</p>
 *
 * <p>陪伴计时（B05）：每用户至多一条 ACTIVE 会话；心跳按服务端时钟差累计
 * {@code lastHeartbeatAt → now} 的有效时长，超过 {@code companionSessionTimeoutSeconds}
 * 视为会话失效（不补计中断区间，再次上报只重建基准）；同一时刻只会计入一份时间
 * （多端共享会话）。有效时长按业务日区间拆分入 {@code pet_companion_daily}
 * （当日计入上限 7200 秒），积分按 entitled-grant 公式发放、已发计数同事务保存。
 * 客户端上报秒数不参与任何收益判定，伪造秒数无法加速。</p>
 */
@Service
@Slf4j
public class PetIntimacyServiceImpl implements PetIntimacyService {

    private final PetMapper petMapper;
    private final PetCompanionSessionMapper sessionMapper;
    private final PetCompanionDailyMapper dailyMapper;
    private final PetProperties properties;
    private final PetEventProducer eventProducer;
    private final PetOutboxService outboxService;
    private final PetClock petClock;

    public PetIntimacyServiceImpl(PetMapper petMapper,
                                  PetCompanionSessionMapper sessionMapper,
                                  PetCompanionDailyMapper dailyMapper,
                                  PetProperties properties,
                                  PetEventProducer eventProducer,
                                  PetOutboxService outboxService,
                                  PetClock petClock) {
        this.petMapper = petMapper;
        this.sessionMapper = sessionMapper;
        this.dailyMapper = dailyMapper;
        this.properties = properties;
        this.eventProducer = eventProducer;
        this.outboxService = outboxService;
        this.petClock = petClock;
    }

    @Override
    public int gain(Pet pet, PetIntimacySource source) {
        int gain = switchGain(source);
        if (gain <= 0 || pet == null) {
            return 0;
        }
        return applyIntimacy(pet, gain);
    }

    /** 原子落库（B05）：目标列自增 + 实体同步；升级事件经 outbox 在业务提交后发送 */
    private int applyIntimacy(Pet pet, int gain) {
        int updated = petMapper.update(null, new LambdaUpdateWrapper<Pet>()
                .setSql("intimacy = intimacy + " + gain)
                .eq(Pet::getId, pet.getId()));
        if (updated == 0) {
            log.warn("亲密度写入未命中（宠物不存在）, petId={}", pet.getId());
            return 0;
        }
        int before = pet.getIntimacy() != null ? pet.getIntimacy() : 0;
        int after = before + gain;
        pet.setIntimacy(after);
        int levelBefore = levelOf(before);
        int levelAfter = levelOf(after);
        if (levelAfter > levelBefore) {
            String eventId = "INTIMACY_LEVEL_UP:" + pet.getId() + ":" + levelAfter;
            outboxService.record(eventId, RocketMQConfig.PET_TAG_INTIMACY, pet.getUserId(), pet.getId(),
                    new PetEventProducer.PetEventMessage(
                            eventId, String.valueOf(pet.getUserId()), "PET_INTIMACY_LEVEL_UP",
                            "我们更亲密啦！",
                            pet.getName() + "：主人，我们已经到「" + levelName(levelAfter) + "」啦，谢谢你一直陪着我～",
                            String.valueOf(pet.getId()), "PET_INTIMACY_LEVEL_UP"));
        }
        return levelAfter - levelBefore;
    }

    @Override
    @Transactional
    public PetCompanionSessionVO heartbeat(Long userId, Integer seconds, Long seq) {
        Pet pet = requireActivePet(userId);
        PetCompanionSession session = activeSession(userId);
        LocalDateTime now = petClock.nowUtc();

        if (session == null || expired(session, now)) {
            if (session != null && expired(session, now)) {
                // 会话失效：不补计中断区间，只重建基准
                endSession(session, "EXPIRED");
            }
            session = startSession(userId, pet, now);
            return toVo(session, now, false, 0, pet);
        }
        // 重复心跳幂等：序号不前进直接返回当前累计
        if (seq != null && session.getLastSeq() != null && seq <= session.getLastSeq()) {
            return toVo(session, now, false, 0, pet);
        }
        long elapsedSeconds = Duration.between(session.getLastHeartbeatAt(), now).getSeconds();
        long timeout = properties.getIntimacy().getCompanionSessionTimeoutSeconds();
        if (elapsedSeconds > timeout) {
            endSession(session, "EXPIRED");
            PetCompanionSession fresh = startSession(userId, pet, now);
            return toVo(fresh, now, false, 0, pet);
        }
        if (elapsedSeconds <= 0) {
            // 同一秒内重复心跳：仅前进序号
            advanceSeq(session, seq);
            return toVo(session, now, false, 0, pet);
        }

        LocalDateTime windowStart = session.getLastHeartbeatAt();
        int credited = creditCompanionSeconds(userId, pet, windowStart, now);
        sessionMapper.update(null, new LambdaUpdateWrapper<PetCompanionSession>()
                .set(PetCompanionSession::getLastHeartbeatAt, now)
                .set(PetCompanionSession::getLastSeq, seq != null ? seq : session.getLastSeq() + 1)
                .eq(PetCompanionSession::getId, session.getId()));
        session.setLastHeartbeatAt(now);
        session.setLastSeq(seq != null ? seq : session.getLastSeq() + 1);
        return toVo(session, now, true, credited, pet);
    }

    @Override
    @Transactional
    public PetCompanionSessionVO stopSession(Long userId) {
        Pet pet = requireActivePet(userId);
        PetCompanionSession session = activeSession(userId);
        LocalDateTime now = petClock.nowUtc();
        if (session == null) {
            return toVo(null, now, false, 0, pet);
        }
        // 正常停止：只结算有效窗口内尚未计入的时间（不超过会话失效间隔）
        long elapsedSeconds = Duration.between(session.getLastHeartbeatAt(), now).getSeconds();
        long timeout = properties.getIntimacy().getCompanionSessionTimeoutSeconds();
        int credited = 0;
        if (elapsedSeconds > 0 && elapsedSeconds <= timeout) {
            credited = creditCompanionSeconds(userId, pet, session.getLastHeartbeatAt(), now);
        }
        endSession(session, "STOPPED");
        return toVo(null, now, true, credited, pet);
    }

    /**
     * 有效时长入账：按业务日区间拆分（跨天不重复计入旧日），各日分别应用计入上限；
     * 同事务保存时长/应得积分/已发积分并发放亲密度。
     *
     * @return 本次实际计入的有效秒数（受当日上限截断）
     */
    private int creditCompanionSeconds(Long userId, Pet pet, LocalDateTime windowStartUtc, LocalDateTime windowEndUtc) {
        PetProperties.Intimacy cfg = properties.getIntimacy();
        LocalDate startBusinessDate = petClock.businessDateOf(windowStartUtc);
        LocalDate endBusinessDate = petClock.businessDateOf(windowEndUtc);

        int totalCredited = 0;
        LocalDate date = startBusinessDate;
        while (!date.isAfter(endBusinessDate)) {
            LocalDateTime segStartUtc = date.equals(startBusinessDate)
                    ? windowStartUtc : petClock.businessDateStartUtc(date);
            LocalDateTime segEndUtc = date.equals(endBusinessDate)
                    ? windowEndUtc : petClock.businessDateStartUtc(date.plusDays(1));
            long segSeconds = Math.max(0, Duration.between(segStartUtc, segEndUtc).getSeconds());

            PetCompanionDaily daily = dailyRow(userId, date);
            int already = daily != null && daily.getAcceptedSeconds() != null ? daily.getAcceptedSeconds() : 0;
            int accepted = (int) Math.min(segSeconds, Math.max(0, cfg.getCompanionDailyCapSeconds() - already));
            if (daily == null) {
                daily = new PetCompanionDaily();
                daily.setUserId(userId);
                daily.setBusinessDate(date);
                daily.setAcceptedSeconds(accepted);
                daily.setGrantedPoints(0);
                try {
                    dailyMapper.insert(daily);
                } catch (DuplicateKeyException e) {
                    daily = dailyRow(userId, date);
                    already = daily != null && daily.getAcceptedSeconds() != null ? daily.getAcceptedSeconds() : 0;
                    accepted = (int) Math.min(segSeconds, Math.max(0, cfg.getCompanionDailyCapSeconds() - already));
                    dailyMapper.update(null, new LambdaUpdateWrapper<PetCompanionDaily>()
                            .setSql("accepted_seconds = accepted_seconds + " + accepted)
                            .eq(PetCompanionDaily::getId, daily.getId()));
                }
            } else {
                dailyMapper.update(null, new LambdaUpdateWrapper<PetCompanionDaily>()
                        .setSql("accepted_seconds = accepted_seconds + " + accepted)
                        .eq(PetCompanionDaily::getId, daily.getId()));
            }
            totalCredited += accepted;

            // 积分：entitled = min(floor(accepted/secondsPerPoint), cap)；grant = entitled - granted
            int grantedSoFar = daily.getGrantedPoints() != null ? daily.getGrantedPoints() : 0;
            int entitled = Math.min((already + accepted) / Math.max(1, cfg.getCompanionSecondsPerPoint()),
                    cfg.getCompanionDailyPointCap());
            int grant = Math.max(0, entitled - grantedSoFar);
            if (grant > 0) {
                dailyMapper.update(null, new LambdaUpdateWrapper<PetCompanionDaily>()
                        .setSql("granted_points = granted_points + " + grant)
                        .eq(PetCompanionDaily::getId, daily.getId()));
                applyIntimacy(pet, grant);
            }
            date = date.plusDays(1);
        }

        // 累计陪伴秒数/天数/连续天数（历史口径，跨天惰性维护）
        maintainLifetimeCounters(pet, petClock.businessDate());
        return totalCredited;
    }

    private void maintainLifetimeCounters(Pet pet, LocalDate businessDate) {
        LocalDate last = pet.getLastCompanionDate();
        if (last != null && businessDate.equals(last)) {
            return;
        }
        boolean consecutive = last != null && last.plusDays(1).equals(businessDate);
        LambdaUpdateWrapper<Pet> wrapper = new LambdaUpdateWrapper<Pet>()
                .set(Pet::getLastCompanionDate, businessDate)
                .eq(Pet::getId, pet.getId());
        if (consecutive) {
            wrapper.setSql("companion_streak = companion_streak + 1");
        } else {
            wrapper.set(Pet::getCompanionStreak, 1);
        }
        wrapper.setSql("companion_days = companion_days + 1");
        petMapper.update(null, wrapper);
        pet.setLastCompanionDate(businessDate);
    }

    private PetCompanionDaily dailyRow(Long userId, LocalDate date) {
        return dailyMapper.selectOne(new LambdaQueryWrapper<PetCompanionDaily>()
                .eq(PetCompanionDaily::getUserId, userId)
                .eq(PetCompanionDaily::getBusinessDate, date)
                .last("LIMIT 1"));
    }

    private PetCompanionSession activeSession(Long userId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<PetCompanionSession>()
                .eq(PetCompanionSession::getUserId, userId)
                .eq(PetCompanionSession::getStatus, "ACTIVE")
                .last("LIMIT 1"));
    }

    private boolean expired(PetCompanionSession session, LocalDateTime now) {
        long timeout = properties.getIntimacy().getCompanionSessionTimeoutSeconds();
        return session.getLastHeartbeatAt() != null
                && Duration.between(session.getLastHeartbeatAt(), now).getSeconds() > timeout;
    }

    /** 新会话建立基准（不凭空增加时长）；每用户仅一条 ACTIVE（先结束旧会话） */
    private PetCompanionSession startSession(Long userId, Pet pet, LocalDateTime now) {
        sessionMapper.update(null, new LambdaUpdateWrapper<PetCompanionSession>()
                .set(PetCompanionSession::getStatus, "ENDED")
                .set(PetCompanionSession::getEndedAt, now)
                .set(PetCompanionSession::getEndReason, "SUPERSEDED")
                .eq(PetCompanionSession::getUserId, userId)
                .eq(PetCompanionSession::getStatus, "ACTIVE"));
        PetCompanionSession session = new PetCompanionSession();
        session.setUserId(userId);
        session.setPetId(pet.getId());
        session.setStatus("ACTIVE");
        session.setStartedAt(now);
        session.setLastHeartbeatAt(now);
        session.setLastSeq(0L);
        sessionMapper.insert(session);
        return session;
    }

    private void endSession(PetCompanionSession session, String reason) {
        sessionMapper.update(null, new LambdaUpdateWrapper<PetCompanionSession>()
                .set(PetCompanionSession::getStatus, "ENDED")
                .set(PetCompanionSession::getEndedAt, petClock.nowUtc())
                .set(PetCompanionSession::getEndReason, reason)
                .eq(PetCompanionSession::getId, session.getId()));
    }

    private void advanceSeq(PetCompanionSession session, Long seq) {
        if (seq == null) {
            return;
        }
        sessionMapper.update(null, new LambdaUpdateWrapper<PetCompanionSession>()
                .set(PetCompanionSession::getLastSeq, seq)
                .eq(PetCompanionSession::getId, session.getId())
                .lt(PetCompanionSession::getLastSeq, seq));
    }

    private PetCompanionSessionVO toVo(PetCompanionSession session, LocalDateTime now,
                                       boolean accepted, int creditedSeconds, Pet pet) {
        PetProperties.Intimacy cfg = properties.getIntimacy();
        LocalDate today = petClock.businessDate();
        PetCompanionDaily daily = dailyRow(pet.getUserId(), today);
        int todayAccepted = daily != null && daily.getAcceptedSeconds() != null ? daily.getAcceptedSeconds() : 0;
        int todayPoints = daily != null && daily.getGrantedPoints() != null ? daily.getGrantedPoints() : 0;
        int intimacy = pet.getIntimacy() != null ? pet.getIntimacy() : 0;
        return new PetCompanionSessionVO(
                session != null ? session.getId() : null,
                session != null ? session.getStatus() : "STOPPED",
                now, accepted, creditedSeconds,
                todayAccepted, todayPoints, cfg.getCompanionDailyPointCap(),
                intimacy, levelOf(intimacy));
    }

    private Pet requireActivePet(Long userId) {
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (pet == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    com.cloudmart.pet.constant.PetErrorCodes.PET_NOT_FOUND, "你还没有宠物");
        }
        return pet;
    }

    @Override
    public int levelOf(int intimacy) {
        return PetIntimacyMath.levelOf(intimacy, properties.getIntimacy().getLevelThresholds());
    }

    @Override
    public String levelName(int level) {
        return PetIntimacyMath.levelName(level, properties.getIntimacy().getLevelNames());
    }

    @Override
    public int toNext(int intimacy) {
        return PetIntimacyMath.toNext(intimacy, properties.getIntimacy().getLevelThresholds());
    }

    @Override
    public double expBonus(Pet pet) {
        return PetIntimacyMath.expBonus(pet, properties.getIntimacy());
    }

    @Override
    public PetIntimacyVO overview(Long userId) {
        Pet pet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, userId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        PetProperties.Intimacy cfg = properties.getIntimacy();
        int intimacy = pet != null && pet.getIntimacy() != null ? pet.getIntimacy() : 0;
        int level = levelOf(intimacy);
        List<Integer> thresholds = cfg.getLevelThresholds();
        List<String> names = cfg.getLevelNames();
        List<PetIntimacyVO.LevelItem> levels = new ArrayList<>();
        for (int i = 0; i < thresholds.size(); i++) {
            levels.add(new PetIntimacyVO.LevelItem(i + 1,
                    i < names.size() ? names.get(i) : "Lv." + (i + 1),
                    thresholds.get(i), intimacy >= thresholds.get(i)));
        }
        int floor = thresholds.isEmpty() ? 0 : thresholds.get(Math.min(level - 1, thresholds.size() - 1));
        Integer nextAt = level < thresholds.size() ? thresholds.get(level) : null;
        int bonusPercent = (int) Math.round(expBonus(pet) * 100);
        // 今日值：跨天惰性读业务日行（当天未心跳也显示正确值 0）
        int todaySeconds = 0;
        if (pet != null) {
            PetCompanionDaily daily = dailyRow(pet.getUserId(), petClock.businessDate());
            todaySeconds = daily != null && daily.getAcceptedSeconds() != null ? daily.getAcceptedSeconds() : 0;
        }
        return new PetIntimacyVO(intimacy, level, levelName(level), floor, nextAt, toNext(intimacy),
                bonusPercent,
                pet != null && pet.getCompanionSeconds() != null ? pet.getCompanionSeconds() : 0L,
                todaySeconds,
                cfg.getCompanionDailyCapSeconds(),
                pet != null && pet.getCompanionDays() != null ? pet.getCompanionDays() : 0,
                pet != null && pet.getCompanionStreak() != null ? pet.getCompanionStreak() : 0,
                levels);
    }

    /** 亲密度来源 → 配置数值（COMPANION 走会话积分公式，不走单次加成） */
    private int switchGain(PetIntimacySource source) {
        PetProperties.Intimacy cfg = properties.getIntimacy();
        return switch (source) {
            case FEED -> cfg.getFeedGain();
            case PLAY -> cfg.getPlayGain();
            case CLEAN -> cfg.getCleanGain();
            case REST -> cfg.getRestGain();
            case CHAT -> cfg.getChatGain();
            case WORK -> cfg.getWorkGain();
            case STUDY -> cfg.getStudyGain();
            case BOTTLE -> cfg.getBottleGain();
            case BATTLE -> cfg.getBattleGain();
            case VISIT -> cfg.getVisitGain();
            case ROOM -> cfg.getRoomGain();
            case WALL -> cfg.getWallGain();
            case QUEST -> cfg.getQuestGain();
            case COMPANION -> 0;
        };
    }
}
