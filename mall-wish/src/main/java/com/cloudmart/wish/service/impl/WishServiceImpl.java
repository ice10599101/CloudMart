package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wish.constant.WishErrorCodes;
import com.cloudmart.wish.dto.CreateWishRequest;
import com.cloudmart.wish.dto.MyWishListQuery;
import com.cloudmart.wish.dto.UpdateWishRequest;
import com.cloudmart.wish.dto.WishListQuery;
import com.cloudmart.wish.entity.Wish;
import com.cloudmart.wish.entity.WishCategory;
import com.cloudmart.wish.entity.WishGrowthRecord;
import com.cloudmart.wish.entity.WishProgress;
import com.cloudmart.wish.enums.AuditStatus;
import com.cloudmart.wish.enums.AuditStrategy;
import com.cloudmart.wish.enums.FruitType;
import com.cloudmart.wish.enums.WishStatus;
import com.cloudmart.wish.enums.WishVisibility;
import com.cloudmart.wish.feign.UserFeignClient;
import com.cloudmart.wish.repository.WishCategoryMapper;
import com.cloudmart.wish.repository.WishGrowthRecordMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.repository.WishProgressMapper;
import com.cloudmart.wish.service.UserStatService;
import com.cloudmart.wish.service.WishService;
import com.cloudmart.wish.util.WishJsonUtils;
import com.cloudmart.wish.vo.MyWishListItemVO;
import com.cloudmart.wish.vo.WishCreateResultVO;
import com.cloudmart.wish.vo.WishDeleteResultVO;
import com.cloudmart.wish.vo.WishGrowthRecordVO;
import com.cloudmart.wish.vo.WishListItemVO;
import com.cloudmart.wish.vo.WishProgressVO;
import com.cloudmart.wish.vo.WishUpdateResultVO;
import com.cloudmart.wish.vo.WishVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 心愿核心服务实现。
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>所有写操作使用 {@code @Transactional(rollbackFor = Exception.class)} 确保原子性</li>
 *   <li>UserStatService 使用 {@code Propagation.MANDATORY} 加入当前事务</li>
 *   <li>作者信息通过 Feign 批量获取（避免 N+1），失败降级为占位值</li>
 *   <li>cursor 分页：按 {@code created_at DESC, id DESC} 排序，游标为 id</li>
 *   <li>PRIVATE/TREE_HOLE 心愿对非作者返回 404（不暴露存在性）</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WishServiceImpl implements WishService {

    private static final int GROWTH_RECORDS_DETAIL_LIMIT = 10;
    private static final int DEFAULT_TARGET_VALUE = 100;
    /** OVERDUE 扫描分批大小（文档定时任务口径：500 条/批） */
    private static final int OVERDUE_SCAN_BATCH_SIZE = 500;

    private final WishMapper wishMapper;
    private final WishCategoryMapper wishCategoryMapper;
    private final WishGrowthRecordMapper wishGrowthRecordMapper;
    private final WishProgressMapper wishProgressMapper;
    private final UserStatService userStatService;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WishCreateResultVO createWish(Long userId, CreateWishRequest request) {
        // 校验分类存在性
        WishCategory category = wishCategoryMapper.selectById(request.categoryId());
        if (category == null) {
            throw new BusinessException(WishErrorCodes.WISH_CATEGORY_INVALID, "心愿分类不存在");
        }

        // 根据 visibility 设置特性字段（文档 1.3 节心愿发布系统规则）
        WishVisibility visibility = request.visibility();
        boolean enableAiReply = Boolean.TRUE.equals(request.enableAiReply());
        boolean triggerEnvEmo = Boolean.TRUE.equals(request.triggerEnvEmo());
        AuditStrategy auditStrategy = AuditStrategy.LAZY;

        if (visibility == WishVisibility.TREE_HOLE) {
            enableAiReply = true;
            triggerEnvEmo = true;
            auditStrategy = AuditStrategy.STRICT;
        } else {
            enableAiReply = false;
            triggerEnvEmo = false;
        }

        Wish wish = new Wish();
        wish.setUserId(userId);
        wish.setTitle(request.title());
        wish.setDescription(request.description());
        wish.setCategoryId(request.categoryId());
        wish.setVisibility(visibility);
        wish.setEnableAiReply(enableAiReply);
        wish.setAuditStrategy(auditStrategy);
        wish.setTriggerEnvEmo(triggerEnvEmo);
        wish.setStatus(WishStatus.ACTIVE);
        wish.setFruitType(FruitType.GLOW);
        wish.setExpectedAt(request.expectedAt());
        wish.setMediaUrls(WishJsonUtils.stringifyList(request.mediaUrls()));
        wish.setTags(WishJsonUtils.stringifyList(request.tags()));
        wish.setSameWishCount(0);
        wish.setLightCount(0);
        wish.setBlessCount(0);
        wish.setAuditStatus(AuditStatus.PENDING);
        wish.setIsVisible(true); // LAZY 审核策略下先可见，STRICT 策略下由审核流程控制

        wishMapper.insert(wish);

        // PUBLIC 心愿上树：固化球面坐标（Sprint 2.1，文档 2.5——坐标一经
        // 写入不变更，果实位置稳定不跳动；insert 后 id 已回填方可计算）
        if (visibility == WishVisibility.PUBLIC) {
            TreePositionCalculator.TreePosition position = TreePositionCalculator.assign(wish.getId());
            wish.setTreeTheta(position.theta());
            wish.setTreePhi(position.phi());
            wishMapper.updateById(wish);
        }

        // 初始化进度记录（1:1 with wish）
        WishProgress progress = new WishProgress();
        progress.setWishId(wish.getId());
        progress.setCurrentValue(0);
        progress.setTargetValue(DEFAULT_TARGET_VALUE);
        progress.setCurrentStreak(0);
        progress.setMaxStreak(0);
        progress.setVersion(0);
        wishProgressMapper.insert(progress);

        // 同事务更新用户统计（创建 +1）
        userStatService.incrementOnWishCreated(userId);

        log.info("心愿创建成功, wishId={}, userId={}, visibility={}", wish.getId(), userId, visibility);

        return new WishCreateResultVO(
                wish.getId(),
                wish.getTitle(),
                wish.getStatus(),
                wish.getFruitType(),
                wish.getCreatedAt()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WishUpdateResultVO updateWish(Long userId, Long wishId, UpdateWishRequest request) {
        Wish wish = getViewableWishOrThrow(wishId, userId);
        assertAuthor(wish, userId);

        // 校验状态：FULFILLED 状态尝试设置 SPARK 果实类型返回 409
        if (request.title() != null) {
            wish.setTitle(request.title());
        }
        if (request.description() != null) {
            wish.setDescription(request.description());
        }
        if (request.mediaUrls() != null) {
            wish.setMediaUrls(WishJsonUtils.stringifyList(request.mediaUrls()));
        }
        if (request.categoryId() != null) {
            // 校验新分类存在性
            WishCategory category = wishCategoryMapper.selectById(request.categoryId());
            if (category == null) {
                throw new BusinessException(WishErrorCodes.WISH_CATEGORY_INVALID, "心愿分类不存在");
            }
            wish.setCategoryId(request.categoryId());
        }
        if (request.tags() != null) {
            wish.setTags(WishJsonUtils.stringifyList(request.tags()));
        }
        if (request.visibility() != null) {
            // 转公开时固化球面坐标（Sprint 2.1：PRIVATE→PUBLIC 上树；
            // 坐标为空才赋值——一经写入不变更，PUBLIC→PRIVATE→PUBLIC 位置不跳动）
            if (request.visibility() == WishVisibility.PUBLIC
                    && wish.getVisibility() != WishVisibility.PUBLIC
                    && wish.getTreeTheta() == null) {
                TreePositionCalculator.TreePosition position = TreePositionCalculator.assign(wish.getId());
                wish.setTreeTheta(position.theta());
                wish.setTreePhi(position.phi());
            }
            wish.setVisibility(request.visibility());
        }
        if (request.expectedAt() != null) {
            wish.setExpectedAt(request.expectedAt());
        }

        wishMapper.updateById(wish);
        log.info("心愿更新成功, wishId={}, userId={}", wishId, userId);

        return new WishUpdateResultVO(wish.getId(), wish.getUpdatedAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WishDeleteResultVO deleteWish(Long userId, Long wishId) {
        Wish wish = getViewableWishOrThrow(wishId, userId);
        assertAuthor(wish, userId);

        // 软删：MyBatis-Plus @TableLogic 自动设置 deleted_at
        wishMapper.deleteById(wishId);

        // 同事务更新用户统计（activeWishes - 1，totalWishes 不变）
        userStatService.decrementOnWishDeleted(userId);

        log.info("心愿软删成功, wishId={}, userId={}", wishId, userId);
        return new WishDeleteResultVO(wishId, LocalDateTime.now());
    }

    @Override
    public WishVO getWishDetail(Long wishId, Long userId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }

        // 可见性校验：PRIVATE/TREE_HOLE 仅作者可见
        if (!isViewableByUser(wish, userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }

        // 分类名称
        WishCategory category = wishCategoryMapper.selectById(wish.getCategoryId());
        String categoryName = category != null ? category.getName() : "";

        // 进度
        WishProgress progress = wishProgressMapper.selectById(wishId);
        WishProgressVO progressVO = toProgressVO(progress);

        // 最近成长记录
        List<WishGrowthRecord> records = wishGrowthRecordMapper.selectList(
                new LambdaQueryWrapper<WishGrowthRecord>()
                        .eq(WishGrowthRecord::getWishId, wishId)
                        .eq(WishGrowthRecord::getIsVisible, true)
                        .orderByDesc(WishGrowthRecord::getCreatedAt)
                        .last("LIMIT " + GROWTH_RECORDS_DETAIL_LIMIT)
        );
        List<WishGrowthRecordVO> recordVOs = records.stream()
                .map(this::toGrowthRecordVO)
                .toList();

        // 打卡天数
        Long checkinDays = 0L; // Sprint 1.1 不含打卡功能，返回 0
        // TODO Sprint 1.3: 从 wish_checkin 表 count

        // 作者信息
        AuthorInfo authorInfo = fetchAuthorInfo(Set.of(wish.getUserId()))
                .getOrDefault(wish.getUserId(), AuthorInfo.placeholder(wish.getUserId()));

        return new WishVO(
                wish.getId(),
                wish.getTitle(),
                wish.getDescription(),
                WishJsonUtils.parseStringList(wish.getMediaUrls()),
                wish.getCategoryId(),
                categoryName,
                WishJsonUtils.parseStringList(wish.getTags()),
                wish.getVisibility(),
                wish.getStatus(),
                wish.getFruitType(),
                wish.getUserId(),
                authorInfo.nickname(),
                authorInfo.avatar(),
                wish.getLightCount(),
                wish.getSameWishCount(),
                wish.getBlessCount(),
                wish.getAnonStarCount(),
                wish.getSupportCount(),
                0, // commentCount: Sprint 1.2 接入 mall-community Feign
                wish.getExpectedAt(),
                wish.getEnableAiReply(),
                wish.getCreatedAt(),
                wish.getUpdatedAt(),
                recordVOs,
                checkinDays.intValue(),
                progressVO
        );
    }

    @Override
    public WishListPage listWishes(WishListQuery query) {
        // 用户端强制 visibility=PUBLIC + auditStatus=APPROVED + isVisible=true
        Long cursor = parseCursor(query.cursor());
        int pageSize = query.pageSize();
        int fetchSize = pageSize + 1; // 多取 1 条判断 hasMore

        LambdaQueryWrapper<Wish> wrapper = new LambdaQueryWrapper<Wish>()
                .eq(Wish::getVisibility, WishVisibility.PUBLIC)
                .eq(Wish::getAuditStatus, AuditStatus.APPROVED)
                .eq(Wish::getIsVisible, true)
                .orderByDesc(Wish::getCreatedAt)
                .orderByDesc(Wish::getId)
                .last("LIMIT " + fetchSize);

        // cursor 条件：id < cursor（按 created_at DESC, id DESC 排序）
        if (cursor != null) {
            wrapper.lt(Wish::getId, cursor);
        }

        // 筛选条件
        if (query.categoryId() != null) {
            wrapper.eq(Wish::getCategoryId, query.categoryId());
        }
        if (query.status() != null) {
            wrapper.eq(Wish::getStatus, query.status());
        } else {
            wrapper.in(Wish::getStatus, WishStatus.ACTIVE, WishStatus.FULFILLING, WishStatus.FULFILLED);
        }
        if (query.keyword() != null && !query.keyword().isBlank()) {
            wrapper.and(w -> w.like(Wish::getTitle, query.keyword())
                    .or().like(Wish::getDescription, query.keyword()));
        }

        List<Wish> wishes = wishMapper.selectList(wrapper);
        boolean hasMore = wishes.size() > pageSize;
        if (hasMore) {
            wishes = wishes.subList(0, pageSize);
        }

        if (wishes.isEmpty()) {
            return new WishListPage(Collections.emptyList(), null, false);
        }

        // 批量填充分类名称
        Set<Long> categoryIds = wishes.stream().map(Wish::getCategoryId).collect(Collectors.toSet());
        Map<Long, String> categoryNameMap = fetchCategoryNames(categoryIds);

        // 批量填充作者信息
        Set<Long> userIds = wishes.stream().map(Wish::getUserId).collect(Collectors.toSet());
        Map<Long, AuthorInfo> authorMap = fetchAuthorInfo(userIds);

        List<WishListItemVO> items = wishes.stream()
                .map(w -> toListItemVO(w, categoryNameMap, authorMap))
                .toList();

        String nextCursor = hasMore ? String.valueOf(wishes.get(wishes.size() - 1).getId()) : null;
        return new WishListPage(items, nextCursor, hasMore);
    }

    @Override
    public MyWishListPage listMyWishes(Long userId, MyWishListQuery query) {
        Long cursor = parseCursor(query.cursor());
        int pageSize = query.pageSize();
        int fetchSize = pageSize + 1;

        LambdaQueryWrapper<Wish> wrapper = new LambdaQueryWrapper<Wish>()
                .eq(Wish::getUserId, userId)
                .orderByDesc(Wish::getCreatedAt)
                .orderByDesc(Wish::getId)
                .last("LIMIT " + fetchSize);

        if (cursor != null) {
            wrapper.lt(Wish::getId, cursor);
        }
        if (query.status() != null) {
            wrapper.eq(Wish::getStatus, query.status());
        }

        List<Wish> wishes = wishMapper.selectList(wrapper);
        boolean hasMore = wishes.size() > pageSize;
        if (hasMore) {
            wishes = wishes.subList(0, pageSize);
        }

        if (wishes.isEmpty()) {
            return new MyWishListPage(Collections.emptyList(), null, false);
        }

        // 批量查询进度
        Set<Long> wishIds = wishes.stream().map(Wish::getId).collect(Collectors.toSet());
        Map<Long, WishProgress> progressMap = wishProgressMapper.selectBatchIds(wishIds)
                .stream()
                .collect(Collectors.toMap(WishProgress::getWishId, p -> p));

        List<MyWishListItemVO> items = wishes.stream()
                .map(w -> {
                    WishProgress p = progressMap.get(w.getId());
                    Integer percentage = (p != null && p.getTargetValue() > 0)
                            ? Math.min(100, p.getCurrentValue() * 100 / p.getTargetValue())
                            : 0;
                    return new MyWishListItemVO(
                            w.getId(),
                            w.getTitle(),
                            w.getStatus(),
                            w.getFruitType(),
                            percentage,
                            w.getLightCount(),
                            w.getCreatedAt()
                    );
                })
                .toList();

        String nextCursor = hasMore ? String.valueOf(wishes.get(wishes.size() - 1).getId()) : null;
        return new MyWishListPage(items, nextCursor, hasMore);
    }

    // --- Helper methods ---

    private Wish getWishOrThrow(Long wishId) {
        Wish wish = wishMapper.selectById(wishId);
        if (wish == null) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        return wish;
    }

    /**
     * 作者级操作（更新/删除）前置获取：对不可见心愿统一返回 404，
     * 避免 PRIVATE/TREE_HOLE 心愿的存在性被非作者探测（NOT_AUTHOR 会泄露资源存在）。
     * 可见但非作者 → WISH_NOT_AUTHOR（403）。
     */
    private Wish getViewableWishOrThrow(Long wishId, Long userId) {
        Wish wish = getWishOrThrow(wishId);
        if (!isViewableByUser(wish, userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_FOUND, "心愿不存在");
        }
        return wish;
    }

    private void assertAuthor(Wish wish, Long userId) {
        if (!wish.getUserId().equals(userId)) {
            throw new BusinessException(WishErrorCodes.WISH_NOT_AUTHOR, "仅作者可操作此心愿");
        }
    }

    private boolean isViewableByUser(Wish wish, Long userId) {
        // 软删的心愿不可见（@TableLogic 已过滤，此处双重保险）
        if (wish.getDeletedAt() != null) {
            return false;
        }
        // 作者始终可见
        if (wish.getUserId().equals(userId)) {
            return true;
        }
        // PRIVATE/TREE_HOLE 非作者不可见
        if (wish.getVisibility() != WishVisibility.PUBLIC) {
            return false;
        }
        // 审核未通过且非作者不可见
        if (wish.getAuditStatus() != AuditStatus.APPROVED && wish.getAuditStatus() != AuditStatus.PENDING) {
            return false;
        }
        // is_visible=false 不可见
        if (Boolean.FALSE.equals(wish.getIsVisible())) {
            return false;
        }
        return true;
    }

    private Long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(WishErrorCodes.WISH_VALIDATION_ERROR, "无效的游标格式");
        }
    }

    private Map<Long, String> fetchCategoryNames(Set<Long> categoryIds) {
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return wishCategoryMapper.selectBatchIds(categoryIds)
                .stream()
                .collect(Collectors.toMap(WishCategory::getId, WishCategory::getName));
    }

    private Map<Long, AuthorInfo> fetchAuthorInfo(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            var response = userFeignClient.batchGetUsers(new ArrayList<>(userIds));
            if (response.success() && response.data() != null) {
                return response.data().stream()
                        .collect(Collectors.toMap(
                                m -> ((Number) m.get("id")).longValue(),
                                m -> new AuthorInfo(
                                        ((Number) m.get("id")).longValue(),
                                        (String) m.getOrDefault("nickname", "心愿旅人"),
                                        (String) m.getOrDefault("avatar", "")
                                )
                        ));
            }
        } catch (Exception e) {
            log.warn("批量获取作者信息失败，降级为占位数据: {}", e.getMessage());
        }
        // 降级：返回占位信息
        return userIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> new AuthorInfo(id, "心愿旅人", "")
                ));
    }

    private WishListItemVO toListItemVO(Wish wish, Map<Long, String> categoryNameMap,
                                        Map<Long, AuthorInfo> authorMap) {
        AuthorInfo author = authorMap.getOrDefault(wish.getUserId(),
                new AuthorInfo(wish.getUserId(), "心愿旅人", ""));
        return new WishListItemVO(
                wish.getId(),
                wish.getTitle(),
                wish.getDescription(),
                WishJsonUtils.parseStringList(wish.getMediaUrls()),
                wish.getCategoryId(),
                categoryNameMap.getOrDefault(wish.getCategoryId(), ""),
                WishJsonUtils.parseStringList(wish.getTags()),
                wish.getVisibility(),
                wish.getStatus(),
                wish.getFruitType(),
                wish.getUserId(),
                author.nickname(),
                author.avatar(),
                wish.getLightCount(),
                wish.getSameWishCount(),
                wish.getBlessCount(),
                wish.getSupportCount(),
                0, // commentCount: Sprint 1.2 接入
                wish.getExpectedAt(),
                wish.getCreatedAt(),
                wish.getUpdatedAt()
        );
    }

    private WishProgressVO toProgressVO(WishProgress progress) {
        if (progress == null) {
            return new WishProgressVO(0, DEFAULT_TARGET_VALUE, 0, 0);
        }
        int percentage = progress.getTargetValue() > 0
                ? Math.min(100, progress.getCurrentValue() * 100 / progress.getTargetValue())
                : 0;
        return new WishProgressVO(
                progress.getCurrentValue(),
                progress.getTargetValue(),
                percentage,
                progress.getVersion()
        );
    }

    private WishGrowthRecordVO toGrowthRecordVO(WishGrowthRecord record) {
        return new WishGrowthRecordVO(
                record.getId(),
                record.getType(),
                record.getContent(),
                WishJsonUtils.parseStringList(record.getMediaUrls()),
                record.getProgressDelta(),
                record.getCreatedAt()
        );
    }

    /** 内部作者信息载体。 */
    private record AuthorInfo(Long userId, String nickname, String avatar) {
        static AuthorInfo placeholder(Long userId) {
            return new AuthorInfo(userId, "心愿旅人", "");
        }
    }

    @Override
    public int scanOverdueWishes() {
        return scanOverdueWishesDetailed().transferred();
    }

    @Override
    public OverdueScanResult scanOverdueWishesDetailed() {
        LocalDateTime now = LocalDateTime.now();
        int totalTransferred = 0;
        List<OverdueWishInfo> transferredWishes = new ArrayList<>();
        // 分批流转（500 条/批）：批间独立提交，单批失败不影响其余批次
        while (true) {
            List<Wish> expiredBatch = wishMapper.selectList(
                    new LambdaQueryWrapper<Wish>()
                            .select(Wish::getId, Wish::getUserId, Wish::getTitle, Wish::getExpectedAt)
                            .eq(Wish::getStatus, WishStatus.ACTIVE)
                            .isNotNull(Wish::getExpectedAt)
                            .lt(Wish::getExpectedAt, now)
                            .isNull(Wish::getDeletedAt)
                            .orderByAsc(Wish::getId)
                            .last("LIMIT " + OVERDUE_SCAN_BATCH_SIZE));
            if (expiredBatch.isEmpty()) {
                break;
            }
            List<Long> batchIds = expiredBatch.stream().map(Wish::getId).toList();
            expiredBatch.forEach(w -> transferredWishes.add(new OverdueWishInfo(
                    w.getId(), w.getUserId(), w.getTitle(), w.getExpectedAt())));
            // UPDATE 附加 status=ACTIVE 条件双保险（查询与更新间状态可能并发变化）
            wishMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Wish>()
                            .in(Wish::getId, batchIds)
                            .eq(Wish::getStatus, WishStatus.ACTIVE)
                            .set(Wish::getStatus, WishStatus.OVERDUE));
            totalTransferred += batchIds.size();
            if (batchIds.size() < OVERDUE_SCAN_BATCH_SIZE) {
                break;
            }
        }
        // 预期管理通知由 InternalJobController 调用 ExpectedManagementService 下发
        // （Sprint 2.5：状态流转与 AI 引导推送解耦，推送失败不回滚流转）
        if (totalTransferred > 0) {
            log.info("OVERDUE 扫描流转完成, count={}", totalTransferred);
        }
        return new OverdueScanResult(totalTransferred, transferredWishes);
    }
}
