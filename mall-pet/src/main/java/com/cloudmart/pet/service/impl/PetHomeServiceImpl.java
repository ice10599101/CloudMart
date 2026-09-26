package com.cloudmart.pet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.pet.config.PetProperties;
import com.cloudmart.pet.config.RocketMQConfig;
import com.cloudmart.pet.constant.PetErrorCodes;
import com.cloudmart.pet.dto.BuyFurnitureRequest;
import com.cloudmart.pet.dto.PlaceFurnitureRequest;
import com.cloudmart.pet.dto.UpdateRoomSettingsRequest;
import com.cloudmart.pet.dto.UpdateRoomThemeRequest;
import com.cloudmart.pet.entity.Pet;
import com.cloudmart.pet.entity.PetFurnitureConfig;
import com.cloudmart.pet.entity.PetInventory;
import com.cloudmart.pet.entity.PetRoom;
import com.cloudmart.pet.entity.PetRoomItem;
import com.cloudmart.pet.enums.PetFurnitureCategory;
import com.cloudmart.pet.enums.PetIntimacySource;
import com.cloudmart.pet.enums.PetItemType;
import com.cloudmart.pet.enums.PetQuestType;
import com.cloudmart.pet.feign.WishFeignClient;
import com.cloudmart.pet.mq.PetEventProducer;
import com.cloudmart.pet.repository.PetFurnitureConfigMapper;
import com.cloudmart.pet.repository.PetInventoryMapper;
import com.cloudmart.pet.repository.PetMapper;
import com.cloudmart.pet.repository.PetRoomItemMapper;
import com.cloudmart.pet.repository.PetRoomMapper;
import com.cloudmart.pet.service.PetAchievementService;
import com.cloudmart.pet.service.PetDailyQuestService;
import com.cloudmart.pet.service.PetHomeService;
import com.cloudmart.pet.service.PetIntimacyService;
import com.cloudmart.pet.service.PetService;
import com.cloudmart.pet.vo.PetHomeItemVO;
import com.cloudmart.pet.vo.PetHomeVO;
import com.cloudmart.pet.vo.PetInventoryItemVO;
import com.cloudmart.pet.vo.PetRoomLikeVO;
import com.cloudmart.pet.vo.PetRoomVisitVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 宠物家园实现（三期）。
 *
 * <p>不变量：
 * <ol>
 *   <li>房间懒创建（{@code uk_pet_room} 兜底并发），老用户不必迁移数据；</li>
 *   <li>家具购买与商城同口径：<b>先入包再扣星光</b>，失败整体回滚；</li>
 *   <li>舒适度是派生值（已摆放家具之和），每次摆放/卸下后重算落库，避免增量漂移；</li>
 *   <li>来访奖励"同一房间每日一次"用 Redis SETNX 保证（Fail-Open 时放行，最长多给一次）；</li>
 *   <li>点赞 uk 在 Redis（每人每房间一次），重复点赞不重复计数。</li>
 * </ol>
 * 家园是"给宠物花时间的地方"：回家/来访/布置都会加亲密度与每日任务进度。</p>
 */
@Service
@Slf4j
public class PetHomeServiceImpl implements PetHomeService {

    static final String KEY_VISIT_DAILY = "pet:ratelimit:homevisit:%d:%s";
    static final String KEY_VISIT_ROOM_TODAY = "pet:home:visited:%d:%d:%s";
    static final String KEY_LIKE_ROOM = "pet:home:liked:%d:%d";
    static final String KEY_LIKE_DAILY = "pet:ratelimit:homelike:%d:%s";
    static final String KEY_ENTER_DAILY = "pet:home:enter:%d:%s";

    private static final String DEFAULT_WELCOME = "欢迎来我家做客，随便坐～";
    private static final String NICKNAME_PLACEHOLDER = "邻居";

    private final PetService petService;
    private final PetStateService stateService;
    private final PetMapper petMapper;
    private final PetRoomMapper roomMapper;
    private final PetRoomItemMapper roomItemMapper;
    private final PetFurnitureConfigMapper furnitureConfigMapper;
    private final PetInventoryMapper inventoryMapper;
    private final WishFeignClient wishFeignClient;
    private final PetOperationService operationService;
    private final PetEventProducer eventProducer;
    private final PetDailyQuestService dailyQuestService;
    private final PetIntimacyService intimacyService;
    private final PetAchievementService achievementService;
    private final PetProperties properties;
    private final StringRedisTemplate redisTemplate;

    public PetHomeServiceImpl(PetService petService,
                              PetStateService stateService,
                              PetMapper petMapper,
                              PetRoomMapper roomMapper,
                              PetRoomItemMapper roomItemMapper,
                              PetFurnitureConfigMapper furnitureConfigMapper,
                              PetInventoryMapper inventoryMapper,
                              WishFeignClient wishFeignClient,
                              PetEventProducer eventProducer,
                              PetDailyQuestService dailyQuestService,
                              PetIntimacyService intimacyService,
                              PetAchievementService achievementService,
                              PetProperties properties,
                              StringRedisTemplate redisTemplate,
                              PetOperationService operationService) {
        this.petService = petService;
        this.stateService = stateService;
        this.petMapper = petMapper;
        this.roomMapper = roomMapper;
        this.roomItemMapper = roomItemMapper;
        this.furnitureConfigMapper = furnitureConfigMapper;
        this.inventoryMapper = inventoryMapper;
        this.wishFeignClient = wishFeignClient;
        this.operationService = operationService;
        this.eventProducer = eventProducer;
        this.dailyQuestService = dailyQuestService;
        this.intimacyService = intimacyService;
        this.achievementService = achievementService;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    @Override
    @Transactional
    public PetHomeVO home(Long userId) {
        Pet pet = petService.requireOwnedPet(userId);
        PetRoom room = ensureRoom(pet);
        boolean enterRewarded = grantDailyEnterReward(pet);
        return buildHomeVo(pet, room, enterRewarded);
    }

    @Override
    @Transactional
    public PetInventoryItemVO buyFurniture(Long userId, BuyFurnitureRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetFurnitureConfig config = furnitureConfigMapper.selectOne(new LambdaQueryWrapper<PetFurnitureConfig>()
                .eq(PetFurnitureConfig::getCode, request.furnitureCode())
                .last("LIMIT 1"));
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_FURNITURE_NOT_FOUND, "这件家具不存在或已下架");
        }
        int requiredLevel = config.getRequiredLevel() != null ? config.getRequiredLevel() : 1;
        if (pet.getLevel() < requiredLevel) {
            throw new BusinessException(PetErrorCodes.PET_LEVEL_REQUIRED,
                    "等级达到 Lv." + requiredLevel + " 才能购买哦");
        }
        PetInventory item = new PetInventory();
        item.setPetId(pet.getId());
        item.setUserId(userId);
        item.setItemType(PetItemType.FURNITURE.name());
        item.setItemCode(config.getCode());
        item.setQuantity(1);
        item.setEquipped(false);
        item.setSlot(config.getCategory());
        item.setAcquiredAt(LocalDateTime.now(ZoneId.of("UTC")));
        try {
            inventoryMapper.insert(item);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ITEM_ALREADY_OWNED, "家里已经有这件家具啦");
        }
        int cost = config.getPriceStarlight() != null ? config.getPriceStarlight() : 0;
        if (cost > 0) {
            String operationId = operationService.operationKey("FURNITURE_BUY",
                    userId, pet.getId(), config.getCode());
            PetOperationService.WalletSettlement settlement = operationService.executeSpend(
                    operationId, userId, pet.getId(), "FURNITURE_BUY", null, cost,
                    com.cloudmart.pet.util.PetJsonUtils.toJson(java.util.Map.of(
                            "itemType", "FURNITURE", "itemCode", config.getCode(), "price", cost)));
            if (settlement.isUnknown()) {
                throw operationService.settlementPending();
            }
            if (!settlement.isCompleted()) {
                throw new BusinessException(PetErrorCodes.PET_VALIDATION_ERROR,
                        "星光扣款未完成: " + settlement.lastError());
            }
        }
        return toInventoryVo(item, config);
    }

    @Override
    @Transactional
    public PetHomeVO place(Long userId, PlaceFurnitureRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetRoom room = ensureRoom(pet);
        PetFurnitureConfig config = requireFurniture(request.furnitureCode());
        PetFurnitureCategory category = PetFurnitureCategory.valueOf(config.getCategory());
        if (category.theme()) {
            throw new BusinessException(PetErrorCodes.PET_FURNITURE_THEME_INVALID,
                    "墙纸和地板要在家园设置里更换哦");
        }
        requireOwned(pet, config.getCode());
        PetProperties.Home cfg = properties.getHome();
        if (request.posX() >= cfg.getGridWidth() || request.posY() >= cfg.getGridHeight()) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_POS_INVALID, "这个位置放不下，换个格子试试");
        }
        Long occupied = roomItemMapper.selectCount(new LambdaQueryWrapper<PetRoomItem>()
                .eq(PetRoomItem::getPetId, pet.getId())
                .eq(PetRoomItem::getPosX, request.posX())
                .eq(PetRoomItem::getPosY, request.posY()));
        if (occupied > 0) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_POS_OCCUPIED, "这个格子已经有家具啦");
        }
        PetRoomItem item = new PetRoomItem();
        item.setPetId(pet.getId());
        item.setUserId(userId);
        item.setFurnitureCode(config.getCode());
        item.setPosX(request.posX());
        item.setPosY(request.posY());
        try {
            roomItemMapper.insert(item);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_POS_OCCUPIED, "这个格子已经有家具啦");
        }
        refreshComfort(room, pet.getId());
        recordDecorate(pet);
        return buildHomeVo(pet, roomMapper.selectById(room.getId()), dailyEnterRewardedRecently(userId));
    }

    @Override
    @Transactional
    public PetHomeVO remove(Long userId, Integer posX, Integer posY) {
        Pet pet = petService.requireOwnedPet(userId);
        PetRoom room = ensureRoom(pet);
        if (posX == null || posY == null) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_POS_INVALID, "请选择要卸下的家具");
        }
        int removed = roomItemMapper.delete(new LambdaQueryWrapper<PetRoomItem>()
                .eq(PetRoomItem::getPetId, pet.getId())
                .eq(PetRoomItem::getPosX, posX)
                .eq(PetRoomItem::getPosY, posY));
        if (removed == 0) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_POS_INVALID, "这个格子上没有家具");
        }
        refreshComfort(room, pet.getId());
        recordDecorate(pet);
        return buildHomeVo(pet, roomMapper.selectById(room.getId()), dailyEnterRewardedRecently(userId));
    }

    @Override
    @Transactional
    public PetHomeVO updateTheme(Long userId, UpdateRoomThemeRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetRoom room = ensureRoom(pet);
        requireThemeOwned(pet, request.wallCode(), PetFurnitureCategory.WALL);
        requireThemeOwned(pet, request.floorCode(), PetFurnitureCategory.FLOOR);
        roomMapper.update(null, new LambdaUpdateWrapper<PetRoom>()
                .set(PetRoom::getWallCode, request.wallCode())
                .set(PetRoom::getFloorCode, request.floorCode())
                .eq(PetRoom::getId, room.getId()));
        recordDecorate(pet);
        return buildHomeVo(pet, roomMapper.selectById(room.getId()), dailyEnterRewardedRecently(userId));
    }

    @Override
    @Transactional
    public PetHomeVO updateSettings(Long userId, UpdateRoomSettingsRequest request) {
        Pet pet = petService.requireOwnedPet(userId);
        PetRoom room = ensureRoom(pet);
        LambdaUpdateWrapper<PetRoom> update = new LambdaUpdateWrapper<PetRoom>()
                .eq(PetRoom::getId, room.getId());
        if (request.isPublic() != null) {
            update.set(PetRoom::getIsPublic, request.isPublic());
        }
        if (request.welcomeMessage() != null) {
            update.set(PetRoom::getWelcomeMessage, request.welcomeMessage());
        }
        roomMapper.update(null, update);
        return buildHomeVo(pet, roomMapper.selectById(room.getId()), dailyEnterRewardedRecently(userId));
    }

    @Override
    @Transactional
    public PetRoomVisitVO visit(Long userId, Long petId) {
        return doVisit(userId, petId, false, false);
    }

    @Override
    @Transactional
    public PetRoomVisitVO visitFriendRoom(Long userId, Long friendUserId) {
        Pet friendPet = petMapper.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getUserId, friendUserId)
                .eq(Pet::getIsActive, true)
                .last("LIMIT 1"));
        if (friendPet == null) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "对方还没有宠物");
        }
        return doVisit(userId, friendPet.getId(), true, true);
    }

    @Override
    @Transactional
    public PetRoomLikeVO like(Long userId, Long petId) {
        Pet pet = petService.requireOwnedPet(userId);
        Pet target = petMapper.selectById(petId);
        if (target == null || !Boolean.TRUE.equals(target.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "对方家的宠物不存在或未公开");
        }
        if (userId.equals(target.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_VISIT_SELF, "给自己点赞不算哦");
        }
        PetRoom room = ensureRoom(target);
        if (!Boolean.TRUE.equals(room.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_PRIVATE, "对方还没有开放家园");
        }
        requireLikeQuota(userId);
        boolean newlyLiked = setIfAbsent(String.format(KEY_LIKE_ROOM, userId, target.getId()), Duration.ofDays(30));
        int rewardExp = 0;
        if (newlyLiked) {
            roomMapper.update(null, new LambdaUpdateWrapper<PetRoom>()
                    .setSql("like_count = like_count + 1")
                    .eq(PetRoom::getId, room.getId()));
            rewardExp = properties.getHome().getLikeRewardExp();
            if (rewardExp > 0) {
                stateService.grantExp(pet, rewardExp);
            }
        }
        PetRoom latest = roomMapper.selectById(room.getId());
        return new PetRoomLikeVO(target.getId(),
                latest != null && latest.getLikeCount() != null ? latest.getLikeCount() : 0,
                newlyLiked, rewardExp,
                newlyLiked
                        ? pet.getName() + " 给 " + target.getName() + " 的小窝点了个赞，经验 +" + rewardExp + "～"
                        : "已经点过赞啦，明天再来看看吧");
    }

    @Override
    public int comfortOf(Long petId) {
        PetRoom room = roomMapper.selectOne(new LambdaQueryWrapper<PetRoom>()
                .eq(PetRoom::getPetId, petId)
                .last("LIMIT 1"));
        return room != null && room.getComfort() != null ? room.getComfort() : 0;
    }

    @Override
    public int comfortRestHappinessBonus(Long petId) {
        PetProperties.Home cfg = properties.getHome();
        int comfort = comfortOf(petId);
        if (comfort < cfg.getComfortBonusThreshold()) {
            return 0;
        }
        // 超过阈值后每 10 点舒适度 +2 心情，封顶配置值（服务端公式，前端只展示结果）
        int bonus = 2 + (comfort - cfg.getComfortBonusThreshold()) / 10 * 2;
        return Math.min(cfg.getComfortRestHappinessBonus(), bonus);
    }

    @Override
    public void recordDecorate(Pet pet) {
        if (pet == null) {
            return;
        }
        try {
            intimacyService.gain(pet, PetIntimacySource.ROOM);
            dailyQuestService.record(pet, PetQuestType.DECORATE, 1);
            achievementService.evaluate(pet, PetAchievementService.Event.ROOM);
        } catch (Exception e) {
            log.warn("家园布置埋点失败（忽略）: petId={}", pet.getId(), e);
        }
    }

    // ---------------- 内部 ----------------

    private PetRoomVisitVO doVisit(Long userId, Long petId, boolean skipDailyLimit, boolean friend) {
        Pet pet = petService.requireOwnedPet(userId);
        Pet target = petMapper.selectById(petId);
        if (target == null || !Boolean.TRUE.equals(target.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_NOT_FOUND, "邻居家的宠物不存在或未公开");
        }
        if (userId.equals(target.getUserId())) {
            throw new BusinessException(PetErrorCodes.PET_VISIT_SELF, "这是自己家，不能来访哦");
        }
        PetRoom room = ensureRoom(target);
        if (!Boolean.TRUE.equals(room.getIsPublic())) {
            throw new BusinessException(PetErrorCodes.PET_ROOM_PRIVATE, "对方还没有开放家园，先串门看看吧");
        }
        if (!skipDailyLimit) {
            requireVisitQuota(userId);
        }
        PetProperties.Home cfg = properties.getHome();
        boolean firstToday = setIfAbsent(
                String.format(KEY_VISIT_ROOM_TODAY, userId, target.getId(), LocalDate.now(ZoneId.of("UTC"))),
                Duration.ofHours(24));
        int rewardHappiness = 0;
        int rewardExp = 0;
        int hostRewardExp = 0;
        if (firstToday) {
            rewardHappiness = cfg.getVisitRewardHappiness();
            rewardExp = cfg.getVisitRewardExp();
            hostRewardExp = cfg.getHostVisitRewardExp();
            pet.setHappiness(Math.min(100, pet.getHappiness() + rewardHappiness));
            intimacyService.gain(pet, PetIntimacySource.VISIT);
            stateService.grantExp(pet, rewardExp);
            // 房主宠物回礼：直接给它发经验（另一行数据，与访客写入互不冲突）
            if (hostRewardExp > 0 && Boolean.TRUE.equals(target.getIsActive())) {
                stateService.grantExp(target, hostRewardExp);
            }
            roomMapper.update(null, new LambdaUpdateWrapper<PetRoom>()
                    .setSql("visit_count = visit_count + 1")
                    .eq(PetRoom::getId, room.getId()));
            dailyQuestService.record(pet, PetQuestType.VISIT, 1);
            eventProducer.publish(RocketMQConfig.PET_TAG_HOME_VISIT, new PetEventProducer.PetEventMessage(
                    "HOME_VISIT:" + pet.getId() + ":" + target.getId() + ":"
                            + java.time.LocalDate.now(java.time.ZoneOffset.UTC),
                    String.valueOf(target.getUserId()), "PET_HOME_VISIT",
                    "有访客来家里啦！",
                    pet.getName() + " 来 " + target.getName() + " 的小窝做客，还夸了夸布置～",
                    String.valueOf(pet.getId()), "PET_HOME_VISIT"));
        }
        PetRoom latest = roomMapper.selectById(room.getId());
        String nickname = resolveNicknames(List.of(target.getUserId()))
                .getOrDefault(target.getUserId(), NICKNAME_PLACEHOLDER);
        boolean liked = Boolean.TRUE.equals(
                redisExists(String.format(KEY_LIKE_ROOM, userId, target.getId())));
        List<PetHomeItemVO> placed = placedItems(target.getId());
        String message = firstToday
                ? pet.getName() + " 参观了 " + target.getName() + " 的小窝，心情 +" + rewardHappiness
                        + "，经验 +" + rewardExp + "～"
                : pet.getName() + " 又来看了一眼，今天已经来过啦（不再重复获得奖励）";
        return new PetRoomVisitVO(
                target.getId(), target.getName(), target.getSpecies(), target.getLevel(),
                target.getEvolutionStage() != null ? target.getEvolutionStage() : 0, target.getSkinCode(),
                nickname,
                latest != null ? latest.getWelcomeMessage() : DEFAULT_WELCOME,
                latest != null ? latest.getWallCode() : null,
                latest != null ? latest.getFloorCode() : null,
                latest != null && latest.getComfort() != null ? latest.getComfort() : 0,
                latest != null && latest.getVisitCount() != null ? latest.getVisitCount() : 0,
                latest != null && latest.getLikeCount() != null ? latest.getLikeCount() : 0,
                liked, !firstToday, rewardHappiness, rewardExp, hostRewardExp,
                friend, message, placed);
    }

    private PetHomeVO buildHomeVo(Pet pet, PetRoom room, boolean enterRewarded) {
        PetProperties.Home cfg = properties.getHome();
        Set<String> ownedCodes = ownedFurnitureCodes(pet.getId());
        List<PetHomeItemVO> placed = placedItems(pet.getId());
        List<PetHomeItemVO> inventory = furnitureConfigMapper.selectList(
                        new LambdaQueryWrapper<PetFurnitureConfig>().eq(PetFurnitureConfig::getEnabled, true))
                .stream()
                .filter(config -> ownedCodes.contains(config.getCode()))
                .map(config -> toItemVo(config, null, null, true, null,
                        config.getCode().equals(room.getWallCode()) || config.getCode().equals(room.getFloorCode())))
                .toList();
        List<PetHomeItemVO> shop = furnitureConfigMapper.selectList(new LambdaQueryWrapper<PetFurnitureConfig>()
                        .eq(PetFurnitureConfig::getEnabled, true)
                        .orderByAsc(PetFurnitureConfig::getSort))
                .stream()
                .map(config -> toShopVo(pet, room, config, ownedCodes.contains(config.getCode())))
                .toList();
        return new PetHomeVO(pet.getId(), pet.getName(), room.getWallCode(), room.getFloorCode(),
                room.getWelcomeMessage(), room.getIsPublic(),
                room.getComfort() != null ? room.getComfort() : 0,
                room.getVisitCount() != null ? room.getVisitCount() : 0,
                room.getLikeCount() != null ? room.getLikeCount() : 0,
                cfg.getGridWidth(), cfg.getGridHeight(), cfg.getComfortBonusThreshold(),
                comfortRestHappinessBonus(pet.getId()), enterRewarded,
                placed, inventory, shop);
    }

    private List<PetHomeItemVO> placedItems(Long petId) {
        List<PetRoomItem> items = roomItemMapper.selectList(new LambdaQueryWrapper<PetRoomItem>()
                .eq(PetRoomItem::getPetId, petId)
                .orderByAsc(PetRoomItem::getPosY)
                .orderByAsc(PetRoomItem::getPosX));
        if (items.isEmpty()) {
            return List.of();
        }
        Map<String, PetFurnitureConfig> configMap = configMap(items.stream()
                .map(PetRoomItem::getFurnitureCode).collect(Collectors.toSet()));
        List<PetHomeItemVO> result = new ArrayList<>();
        for (PetRoomItem item : items) {
            PetFurnitureConfig config = configMap.get(item.getFurnitureCode());
            if (config == null) {
                continue;
            }
            result.add(toItemVo(config, item.getPosX(), item.getPosY(), true, null, false));
        }
        return result;
    }

    private PetHomeItemVO toShopVo(Pet pet, PetRoom room, PetFurnitureConfig config, boolean owned) {
        String lockReason = null;
        int requiredLevel = config.getRequiredLevel() != null ? config.getRequiredLevel() : 1;
        if (pet.getLevel() < requiredLevel) {
            lockReason = "需要 Lv." + requiredLevel;
        }
        boolean themeActive = config.getCode().equals(room.getWallCode())
                || config.getCode().equals(room.getFloorCode());
        return toItemVo(config, null, null, owned, lockReason, themeActive);
    }

    private PetHomeItemVO toItemVo(PetFurnitureConfig config, Integer posX, Integer posY,
                                   boolean owned, String lockReason, boolean themeActive) {
        PetFurnitureCategory category = PetFurnitureCategory.valueOf(config.getCategory());
        return new PetHomeItemVO(config.getCode(), config.getName(), config.getDescription(),
                config.getCategory(), category.label(), config.getIcon(), config.getRarity(),
                config.getComfort() != null ? config.getComfort() : 0,
                config.getPriceStarlight(), config.getRequiredLevel(), posX, posY,
                owned, lockReason == null, lockReason, themeActive);
    }

    private PetInventoryItemVO toInventoryVo(PetInventory item, PetFurnitureConfig config) {
        return new PetInventoryItemVO(PetItemType.FURNITURE.name(), config.getCode(), config.getName(),
                config.getDescription(), config.getIcon(), config.getRarity(), config.getCategory(),
                null, null, config.getCategory(),
                java.math.BigDecimal.valueOf(config.getComfort() != null ? config.getComfort() : 0),
                0, 0, 0, 0, 0,
                Boolean.TRUE.equals(item.getEquipped()), item.getQuantity(), false, item.getAcquiredAt());
    }

    /** 房间懒创建：uk_pet_room 兜底并发（并发时重读） */
    private PetRoom ensureRoom(Pet pet) {
        PetRoom room = roomMapper.selectOne(new LambdaQueryWrapper<PetRoom>()
                .eq(PetRoom::getPetId, pet.getId())
                .last("LIMIT 1"));
        if (room != null) {
            return room;
        }
        PetRoom created = new PetRoom();
        created.setPetId(pet.getId());
        created.setUserId(pet.getUserId());
        created.setWelcomeMessage(DEFAULT_WELCOME);
        created.setIsPublic(true);
        created.setComfort(0);
        created.setVisitCount(0);
        created.setLikeCount(0);
        try {
            roomMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            return roomMapper.selectOne(new LambdaQueryWrapper<PetRoom>()
                    .eq(PetRoom::getPetId, pet.getId())
                    .last("LIMIT 1"));
        }
    }

    /** 舒适度重算（派生值，避免增量漂移） */
    private void refreshComfort(PetRoom room, Long petId) {
        List<PetRoomItem> items = roomItemMapper.selectList(new LambdaQueryWrapper<PetRoomItem>()
                .eq(PetRoomItem::getPetId, petId));
        int comfort = 0;
        if (!items.isEmpty()) {
            Map<String, PetFurnitureConfig> configMap = configMap(items.stream()
                    .map(PetRoomItem::getFurnitureCode).collect(Collectors.toSet()));
            for (PetRoomItem item : items) {
                PetFurnitureConfig config = configMap.get(item.getFurnitureCode());
                if (config != null && config.getComfort() != null) {
                    comfort += config.getComfort();
                }
            }
        }
        roomMapper.update(null, new LambdaUpdateWrapper<PetRoom>()
                .set(PetRoom::getComfort, comfort)
                .eq(PetRoom::getId, room.getId()));
    }

    /** 回家奖励：每日首次进入加心情/经验/亲密度（Redis SETNX 一次/天） */
    private boolean grantDailyEnterReward(Pet pet) {
        PetProperties.Home cfg = properties.getHome();
        boolean first = setIfAbsent(
                String.format(KEY_ENTER_DAILY, pet.getUserId(), LocalDate.now(ZoneId.of("UTC"))),
                Duration.ofHours(24));
        if (!first) {
            return false;
        }
        pet.setHappiness(Math.min(100, pet.getHappiness() + cfg.getDailyEnterHappiness()));
        intimacyService.gain(pet, PetIntimacySource.ROOM);
        stateService.grantExp(pet, cfg.getDailyEnterExp());
        achievementService.evaluate(pet, PetAchievementService.Event.ROOM);
        return true;
    }

    private boolean dailyEnterRewardedRecently(Long userId) {
        return Boolean.TRUE.equals(redisExists(
                String.format(KEY_ENTER_DAILY, userId, LocalDate.now(ZoneId.of("UTC")))));
    }

    private Map<String, PetFurnitureConfig> configMap(Set<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return furnitureConfigMapper.selectList(new LambdaQueryWrapper<PetFurnitureConfig>()
                        .in(PetFurnitureConfig::getCode, codes))
                .stream()
                .collect(Collectors.toMap(PetFurnitureConfig::getCode, Function.identity(), (a, b) -> a));
    }

    private PetFurnitureConfig requireFurniture(String code) {
        PetFurnitureConfig config = furnitureConfigMapper.selectOne(new LambdaQueryWrapper<PetFurnitureConfig>()
                .eq(PetFurnitureConfig::getCode, code)
                .last("LIMIT 1"));
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(PetErrorCodes.PET_FURNITURE_NOT_FOUND, "这件家具不存在或已下架");
        }
        return config;
    }

    private void requireOwned(Pet pet, String code) {
        Long owned = inventoryMapper.selectCount(new LambdaQueryWrapper<PetInventory>()
                .eq(PetInventory::getPetId, pet.getId())
                .eq(PetInventory::getItemType, PetItemType.FURNITURE.name())
                .eq(PetInventory::getItemCode, code));
        if (owned == 0) {
            throw new BusinessException(PetErrorCodes.PET_FURNITURE_NOT_OWNED, "还没有这件家具，先去家园商城买吧");
        }
    }

    private void requireThemeOwned(Pet pet, String code, PetFurnitureCategory expected) {
        if (code == null || code.isBlank()) {
            return;
        }
        PetFurnitureConfig config = requireFurniture(code);
        if (!expected.name().equals(config.getCategory())) {
            throw new BusinessException(PetErrorCodes.PET_FURNITURE_THEME_INVALID,
                    expected == PetFurnitureCategory.WALL ? "墙纸要选墙纸类家具" : "地板要选地板类家具");
        }
        requireOwned(pet, code);
    }

    private Set<String> ownedFurnitureCodes(Long petId) {
        Set<String> codes = new HashSet<>();
        inventoryMapper.selectList(new LambdaQueryWrapper<PetInventory>()
                        .eq(PetInventory::getPetId, petId)
                        .eq(PetInventory::getItemType, PetItemType.FURNITURE.name()))
                .forEach(item -> codes.add(item.getItemCode()));
        return codes;
    }

    private void requireVisitQuota(Long userId) {
        try {
            String key = String.format(KEY_VISIT_DAILY, userId, LocalDate.now(ZoneId.of("UTC")));
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            int limit = properties.getHome().getDailyVisitLimit();
            if (used != null && used > limit) {
                throw new BusinessException(PetErrorCodes.PET_INTERACTION_RATE_LIMITED,
                        "今天已经逛了 " + limit + " 个家啦，明天再去吧");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("家园访问限频 Redis 故障，Fail-Open 放行: userId={}", userId, e);
        }
    }

    private void requireLikeQuota(Long userId) {
        try {
            String key = String.format(KEY_LIKE_DAILY, userId, LocalDate.now(ZoneId.of("UTC")));
            Long used = redisTemplate.opsForValue().increment(key);
            if (used != null && used == 1L) {
                redisTemplate.expire(key, Duration.ofHours(24));
            }
            int limit = properties.getHome().getDailyLikeLimit();
            if (used != null && used > limit) {
                throw new BusinessException(PetErrorCodes.PET_INTERACTION_RATE_LIMITED,
                        "今天已经点了很多赞啦，明天再继续吧");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("点赞限频 Redis 故障，Fail-Open 放行: userId={}", userId, e);
        }
    }

    /** Redis SETNX + TTL（Fail-Open：Redis 故障视为首次，宁可多给一次也不阻断） */
    private boolean setIfAbsent(String key, Duration ttl) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", ttl));
        } catch (Exception e) {
            log.warn("家园幂等键写入降级（Fail-Open）: key={}", key, e);
            return true;
        }
    }

    private Boolean redisExists(String key) {
        try {
            return redisTemplate.hasKey(key);
        } catch (Exception e) {
            log.warn("家园状态查询降级（Fail-Open）: key={}", key, e);
            return false;
        }
    }

    /** 昵称批量查询：展示型数据 Fail-Open（占位昵称） */
    private Map<Long, String> resolveNicknames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> users = wishFeignClient.batchGetUsers(userIds).data();
            if (users == null) {
                return Map.of();
            }
            Map<Long, String> result = new HashMap<>();
            for (Map<String, Object> user : users) {
                Object id = user.get("id");
                Object nickname = user.get("nickname");
                if (id instanceof Number numberId && nickname != null) {
                    result.put(numberId.longValue(), nickname.toString());
                }
            }
            return result;
        } catch (Exception e) {
            return Map.of();
        }
    }
}
