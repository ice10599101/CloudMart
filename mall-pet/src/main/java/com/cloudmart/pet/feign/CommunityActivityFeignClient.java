package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * mall-wish 社区活动客户端（原文档 §28.6 活动提醒：社区活动即将结束）。
 * GET /activities 为公开浏览端点（permitAll），内部调用额外携带内部头。
 */
@FeignClient(name = "mall-wish", contextId = "petActivityFeignClient",
        fallbackFactory = CommunityActivityFeignClientFallbackFactory.class)
public interface CommunityActivityFeignClient {

    /** 活动列表（仅 ACTIVE 且展示期内） */
    @GetMapping("/activities")
    ApiResponse<List<CommunityActivityVO>> listActivities();

    /** 活动条目（宠物模块消费的字段子集；时间字段以 String 承接避免跨服务类型耦合） */
    record CommunityActivityVO(
            Long id,
            String title,
            String status,
            String validTo
    ) {
    }
}
