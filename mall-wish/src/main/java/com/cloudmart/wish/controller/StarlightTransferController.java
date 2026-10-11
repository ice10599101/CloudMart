package com.cloudmart.wish.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.wish.service.StarlightTransferService;
import com.cloudmart.wish.vo.StarlightTransferVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 星光转赠（§6/R06）：守恒等额对转 + 原子日限额 + 持久幂等。
 */
@RestController
@RequestMapping("/starlight/transfers")
@RequiredArgsConstructor
@Validated
@Tag(name = "星光转赠", description = "好友间转赠（单笔 10..100，日累计 ≤200 原子占用；守恒等额对转）")
public class StarlightTransferController {

    private final StarlightTransferService transferService;

    public record TransferRequest(
            @NotNull(message = "转入用户不能为空") Long toUserId,
            @NotNull(message = "数量不能为空")
            @Min(value = 10, message = "单笔至少 10 星光")
            @Max(value = 100, message = "单笔至多 100 星光") Integer amount,
            @Size(max = 100, message = "附言最长 100 字") String message,
            @Size(max = 64, message = "请求键过长") String requestKey) {
    }

    // @Operation 置于 @Mapping 之前（规避 route-inventory 扫描器把下一注解首串当子路径）
    @Operation(summary = "转赠", description = "非自转、好友（任一关注方向）、日累计 ≤200 原子占用；"
            + "等额对转守恒（接收余量不足整笔拒绝）；requestKey 持久幂等，同键异载荷 409")
    @PostMapping
    public ApiResponse<StarlightTransferVO> transfer(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody TransferRequest request) {
        return ApiResponse.ok(transferService.transfer(userId, request.toUserId(),
                request.amount(), request.message(), request.requestKey()));
    }

    @Operation(summary = "我的日转出额度", description = "cap/已用/剩余（服务端 Asia/Shanghai 业务日）；确认页展示用")
    @GetMapping("/limits")
    public ApiResponse<StarlightTransferService.TransferLimits> limits(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId) {
        return ApiResponse.ok(transferService.transferLimits(userId));
    }

    @Operation(summary = "我的转赠记录", description = "发出+收到合并倒序，分页（records/total）")
    @GetMapping("/my")
    public ApiResponse<StarlightTransferService.TransferPage> my(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize) {
        return ApiResponse.ok(transferService.myTransfers(userId, page, pageSize));
    }
}
