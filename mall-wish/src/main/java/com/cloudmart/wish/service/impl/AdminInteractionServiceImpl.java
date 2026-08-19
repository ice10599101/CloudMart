package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.wish.dto.AdminInteractionListQuery;
import com.cloudmart.wish.entity.WishInteraction;
import com.cloudmart.wish.repository.WishInteractionMapper;
import com.cloudmart.wish.service.AdminInteractionService;
import com.cloudmart.wish.vo.AdminInteractionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台互动服务实现（Sprint 1.2）。
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用 selectPageIncludingDeleted 绕过 @TableLogic，保留已取消互动的完整审计轨迹</li>
 *   <li>心愿标题/用户昵称批量填充（避免 N+1），Feign 失败降级占位昵称</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminInteractionServiceImpl implements AdminInteractionService {

    private static final String NICKNAME_PLACEHOLDER = "心愿旅人";

    private final WishInteractionMapper wishInteractionMapper;
    private final AdminDisplayInfoResolver displayInfoResolver;

    @Override
    public Page<AdminInteractionVO> listInteractions(AdminInteractionListQuery query) {
        LambdaQueryWrapper<WishInteraction> wrapper = new LambdaQueryWrapper<>();
        if (query.wishId() != null) {
            wrapper.eq(WishInteraction::getWishId, query.wishId());
        }
        if (query.userId() != null) {
            wrapper.eq(WishInteraction::getUserId, query.userId());
        }
        if (query.type() != null) {
            wrapper.eq(WishInteraction::getType, query.type());
        }
        if (query.startTime() != null) {
            wrapper.ge(WishInteraction::getCreatedAt, query.startTime());
        }
        if (query.endTime() != null) {
            wrapper.le(WishInteraction::getCreatedAt, query.endTime());
        }
        wrapper.orderByDesc(WishInteraction::getId);

        Page<WishInteraction> page = new Page<>(query.page(), query.pageSize());
        Page<WishInteraction> interactionPage =
                wishInteractionMapper.selectPageIncludingDeleted(page, wrapper);

        List<WishInteraction> records = interactionPage.getRecords();
        Map<Long, String> titleMap = displayInfoResolver.fetchWishTitles(collectWishIds(records));
        Map<Long, String> nicknameMap = displayInfoResolver.fetchUserNicknames(collectUserIds(records));

        List<AdminInteractionVO> voList = records.stream()
                .map(i -> toVO(i, titleMap, nicknameMap))
                .toList();

        Page<AdminInteractionVO> resultPage =
                new Page<>(interactionPage.getCurrent(), interactionPage.getSize(), interactionPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    private Set<Long> collectWishIds(List<WishInteraction> records) {
        return records.stream().map(WishInteraction::getWishId).collect(Collectors.toSet());
    }

    private Set<Long> collectUserIds(List<WishInteraction> records) {
        return records.stream().map(WishInteraction::getUserId).collect(Collectors.toSet());
    }

    private AdminInteractionVO toVO(WishInteraction interaction,
                                    Map<Long, String> titleMap,
                                    Map<Long, String> nicknameMap) {
        return new AdminInteractionVO(
                interaction.getId(),
                interaction.getWishId(),
                titleMap.getOrDefault(interaction.getWishId(), ""),
                interaction.getUserId(),
                nicknameMap.getOrDefault(interaction.getUserId(), NICKNAME_PLACEHOLDER),
                interaction.getType(),
                interaction.getContent(),
                interaction.getStarlightCost(),
                interaction.getDeletedAt(),
                interaction.getCreatedAt()
        );
    }
}
