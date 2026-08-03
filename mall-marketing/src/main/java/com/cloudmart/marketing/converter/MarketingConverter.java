package com.cloudmart.marketing.converter;

import com.cloudmart.marketing.dto.*;
import com.cloudmart.marketing.entity.*;
import com.cloudmart.marketing.vo.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MarketingConverter {

    @Mapping(target = "createdAt", source = "createdAt")
    GroupActivityDTO toDTO(GroupActivity entity);

    List<GroupActivityDTO> toActivityDTOList(List<GroupActivity> entities);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "currentGroups", constant = "0")
    @Mapping(target = "status", constant = "DISABLED")
    GroupActivity toEntity(CreateGroupActivityRequest request);

    GroupMemberDTO toDTO(GroupMember entity);

    List<GroupMemberDTO> toMemberDTOList(List<GroupMember> entities);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isLeader", constant = "false")
    @Mapping(target = "orderId", ignore = true)
    @Mapping(target = "status", constant = "JOINED")
    @Mapping(target = "joinedAt", expression = "java(java.time.LocalDateTime.now())")
    GroupMember toEntity(Long groupOrderId, Long userId, Long activityId, Boolean isLeader);

    @Mapping(target = "createdAt", source = "createdAt")
    TieredPromotionDTO toDTO(TieredPromotion entity);

    List<TieredPromotionDTO> toPromotionDTOList(List<TieredPromotion> entities);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "status", constant = "DISABLED")
    TieredPromotion toEntity(CreateTieredPromotionRequest request);

    TieredRuleDTO toDTO(TieredPromotionRule entity);

    List<TieredRuleDTO> toRuleDTOList(List<TieredPromotionRule> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    TieredPromotionRule toEntity(Long promotionId, TieredRuleRequest rule);

    GroupActivityVO toGroupActivityVO(GroupActivity entity);

    List<GroupActivityVO> toGroupActivityVOList(List<GroupActivity> entities);

    GroupOrderVO toGroupOrderVO(GroupOrder entity);

    GroupMemberVO toGroupMemberVO(GroupMember entity);

    List<GroupMemberVO> toGroupMemberVOList(List<GroupMember> entities);

    @Mapping(target = "type", constant = "TIERED")
    TieredPromotionVO toTieredPromotionVO(TieredPromotion entity);

    List<TieredPromotionVO> toTieredPromotionVOList(List<TieredPromotion> entities);

    GroupActivityVO groupActivityDtoToVO(GroupActivityDTO dto);

    default List<GroupActivityVO> groupActivityDtoListToVOList(List<GroupActivityDTO> dtos) {
        return dtos.stream().map(this::groupActivityDtoToVO).toList();
    }

    GroupOrderVO groupOrderDtoToVO(GroupOrderDTO dto);

    @Mapping(target = "type", constant = "TIERED")
    @Mapping(target = "rules", ignore = true)
    TieredPromotionVO tieredPromotionDtoToVO(TieredPromotionDTO dto);

    default TieredPromotionVO tieredPromotionDtoToVOWithRules(TieredPromotionDTO dto) {
        TieredPromotionVO base = tieredPromotionDtoToVO(dto);
        List<TieredRuleVO> ruleVOs = dto.rules() != null
                ? dto.rules().stream()
                .map(r -> new TieredRuleVO(r.id(), r.minAmount(), r.discountAmount()))
                .toList()
                : List.of();
        return new TieredPromotionVO(base.id(), base.name(), base.type(),
                base.startTime(), base.endTime(), base.status(), ruleVOs);
    }
}
