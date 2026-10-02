package com.cloudmart.marketing.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.marketing.entity.GroupActivity;
import com.cloudmart.marketing.entity.GroupMember;
import com.cloudmart.marketing.entity.GroupOrder;
import com.cloudmart.marketing.repository.GroupActivityMapper;
import com.cloudmart.marketing.repository.GroupMemberMapper;
import com.cloudmart.marketing.repository.GroupOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 内部拼团快照接口（T10）：mall-order 建成团订单前经服务令牌回查，
 * 校验成团状态与成员归属，以活动拼团价（groupPrice 快照）计价——
 * 拼团价与普通价不混用，消息载荷不作为定价依据。
 */
@RestController
@RequestMapping("/internal/groups")
@Tag(name = "内部-拼团快照", description = "mall-order 建成团订单回查（服务令牌可达）")
public class InternalGroupController {

    private final GroupOrderMapper groupOrderMapper;
    private final GroupActivityMapper activityMapper;
    private final GroupMemberMapper memberMapper;

    public InternalGroupController(GroupOrderMapper groupOrderMapper,
                                   GroupActivityMapper activityMapper,
                                   GroupMemberMapper memberMapper) {
        this.groupOrderMapper = groupOrderMapper;
        this.activityMapper = activityMapper;
        this.memberMapper = memberMapper;
    }

    /**
     * 成团快照：仅 SUCCESS 状态可查（未成团/已过期的组不能建单）。
     */
    @GetMapping("/{groupOrderId}")
    @PreAuthorize("hasRole('INTERNAL')")
    @Operation(summary = "查询成团快照", description = "groupOrderId → 成团快照（成员/商品/价格）；未成团抛业务异常")
    public ApiResponse<GroupQuoteDTO> quote(
            @Parameter(description = "拼团组ID", required = true) @PathVariable("groupOrderId") Long groupOrderId) {
        GroupOrder groupOrder = groupOrderMapper.selectById(groupOrderId);
        if (groupOrder == null) {
            throw new BusinessException("GROUP_NOT_FOUND", "拼团组不存在");
        }
        if (!"SUCCESS".equals(groupOrder.getStatus())) {
            throw new BusinessException("GROUP_NOT_SUCCESS", "拼团组未成团，不能创建订单");
        }
        GroupActivity activity = activityMapper.selectById(groupOrder.getActivityId());
        if (activity == null) {
            throw new BusinessException("ACTIVITY_NOT_FOUND", "拼团活动不存在");
        }
        List<Long> memberUserIds = memberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                        .eq(GroupMember::getGroupOrderId, groupOrderId))
                .stream().map(GroupMember::getUserId).toList();
        return ApiResponse.ok(new GroupQuoteDTO(groupOrderId, activity.getId(), activity.getProductId(),
                activity.getSkuId(), activity.getGroupPrice(), memberUserIds));
    }

    /** 成团快照（内部） */
    public record GroupQuoteDTO(Long groupOrderId, Long activityId, Long productId, Long skuId,
                                BigDecimal groupPrice, List<Long> memberUserIds) {
    }
}
