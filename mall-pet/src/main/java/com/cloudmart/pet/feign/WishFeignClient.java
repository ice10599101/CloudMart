package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.pet.config.WishServiceTokenConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * mall-wish 内部能力客户端：仅保留宠物代主人捞漂流瓶。
 * 批量用户信息走 {@link UserFeignClient}（mall-user 才是归属服务）；
 * 宠物经济只经本地 PET_COIN 钱包结算，不再经 wish 发放/扣减星光。
 *
 * <p>复用边界：不建第二套漂流瓶实现；
 * 降级时抛 {@code WISH_SERVICE_UNAVAILABLE}（GlobalExceptionHandler 后缀通配映射 503）。</p>
 */
@FeignClient(name = "mall-wish", contextId = "petWishFeignClient",
        configuration = WishServiceTokenConfig.class,
        fallbackFactory = WishFeignClientFallbackFactory.class)
public interface WishFeignClient {

    /** 宠物代主人捞瓶：随机候选 + CAS 抢瓶，计入用户每日打捞配额；海里无瓶 data=null。
     *  userId 显式传入（定时任务线程无登录头）；requestId 幂等（B11：同标识重入返回原结果） */
    @PostMapping("/internal/pet-support/drift-bottles/fish")
    ApiResponse<WishBottleVO> fishForPet(@RequestParam("userId") Long userId,
                                         @RequestParam(value = "requestId", required = false) String requestId);

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
