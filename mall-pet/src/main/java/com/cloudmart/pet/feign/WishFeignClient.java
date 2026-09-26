package com.cloudmart.pet.feign;

import com.cloudmart.common.api.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * mall-wish 内部能力客户端：宠物代主人捞瓶 + 宠物奖励发星光 + 批量用户信息。
 *
 * <p>复用边界（实施文档 §0.1 原则 2/3）：不建第二套漂流瓶/钱包；
 * 降级时抛 {@code WISH_SERVICE_UNAVAILABLE}（GlobalExceptionHandler 后缀通配映射 503）。</p>
 *
 * <p>B01 幂等契约：earn/spend 携带调用方生成的 {@code operationId}（业务操作唯一键），
 * 钱包按其原子去重——重复相同请求返回原结果（{@code duplicate=true}），同键不同内容
 * 返回 409 WISH_OPERATION_CONFLICT。结果未知时可经 {@link #findOperation} 按原单查询。</p>
 */
@FeignClient(name = "mall-wish", contextId = "petWishFeignClient",
        fallbackFactory = WishFeignClientFallbackFactory.class)
public interface WishFeignClient {

    /** 宠物代主人捞瓶：随机候选 + CAS 抢瓶，计入用户每日打捞配额；海里无瓶 data=null。
     *  userId 显式传入（定时任务线程无登录头）；requestId 幂等（B11：同标识重入返回原结果） */
    @PostMapping("/internal/pet-support/drift-bottles/fish")
    ApiResponse<WishBottleVO> fishForPet(@RequestParam("userId") Long userId,
                                         @RequestParam(value = "requestId", required = false) String requestId);

    /** 幂等发放星光（PET_REWARD 流水），返回实际入账量（余额上限截断）与操作后余额 */
    @PostMapping("/internal/pet-support/starlight/earn")
    ApiResponse<PetWalletOperationVO> earnStarlightIdempotent(@RequestParam("userId") Long userId,
                                                              @RequestParam("amount") Integer amount,
                                                              @RequestParam("refId") Long refId,
                                                              @RequestParam("operationId") String operationId);

    /** 幂等扣减星光（PET_SHOP 流水）；余额不足由 mall-wish 返回 402 */
    @PostMapping("/internal/pet-support/starlight/spend")
    ApiResponse<PetWalletOperationVO> spendStarlightIdempotent(@RequestParam("userId") Long userId,
                                                               @RequestParam("amount") Integer amount,
                                                               @RequestParam("refId") Long refId,
                                                               @RequestParam("operationId") String operationId);

    /** 交易结果查询（B01 内部结果查询）：data=null 表示结果未知，可按原单安全重试 */
    @GetMapping("/internal/pet-support/starlight/operations/{operationId}")
    ApiResponse<PetWalletOperationVO> findOperation(@PathVariable("operationId") String operationId);

    /** 星光余额（商城展示）；失败由 fallback 抛 WISH_SERVICE_UNAVAILABLE */
    @GetMapping("/internal/pet-support/starlight/balance")
    ApiResponse<Integer> starlightBalance(@RequestParam("userId") Long userId);

    /** 批量用户信息（对战对手主人昵称；字段取子集） */
    @GetMapping("/users/batch")
    ApiResponse<List<Map<String, Object>>> batchGetUsers(@RequestParam("ids") List<Long> ids);

    /** 幂等交易结果（与 mall-wish PetWalletOperationVO 契约对齐；ID 以字符串往返） */
    record PetWalletOperationVO(
            String operationId,
            String operationType,
            Integer amount,
            Integer creditedAmount,
            Integer balanceAfter,
            String source,
            Long refId,
            String status,
            boolean duplicate
    ) {
    }

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
