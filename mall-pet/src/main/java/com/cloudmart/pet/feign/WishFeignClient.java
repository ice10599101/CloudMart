package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * mall-wish 内部能力客户端：宠物代主人捞瓶 + 宠物奖励发星光 + 批量用户信息。
 *
 * <p>复用边界（实施文档 §0.1 原则 2/3）：不建第二套漂流瓶/钱包；
 * 降级时抛 {@code WISH_SERVICE_UNAVAILABLE}（GlobalExceptionHandler 后缀通配映射 503）。</p>
 */
@FeignClient(name = "mall-wish", contextId = "petWishFeignClient",
        fallbackFactory = WishFeignClientFallbackFactory.class)
public interface WishFeignClient {

    /** 宠物代主人捞瓶：随机候选 + CAS 抢瓶，计入用户每日打捞配额；海里无瓶 data=null */
    @PostMapping("/internal/pet-support/drift-bottles/fish")
    ApiResponse<WishBottleVO> fishForPet();

    /** 宠物奖励发放星光（wish_resource_log 流水来源 PET_REWARD），返回发放后余额 */
    @PostMapping("/internal/pet-support/starlight/earn")
    ApiResponse<Integer> earnStarlight(@RequestParam("userId") Long userId,
                                       @RequestParam("amount") Integer amount,
                                       @RequestParam("refId") Long refId);

    /** 批量用户信息（对战对手主人昵称；字段取子集） */
    @GetMapping("/users/batch")
    ApiResponse<List<Map<String, Object>>> batchGetUsers(@RequestParam("ids") List<Long> ids);

    /** 捞瓶结果（仅宠物模块消费的字段子集；时间字段以 String 承接避免跨服务类型耦合） */
    record WishBottleVO(
            Long bottleId,
            String status,
            String role,
            String content,
            Long wishId,
            String wishTitle
    ) {
    }
}
