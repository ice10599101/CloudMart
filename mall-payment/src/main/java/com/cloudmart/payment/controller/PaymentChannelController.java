package com.cloudmart.payment.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.payment.channel.PaymentScene;
import com.cloudmart.payment.dto.ChannelView;
import com.cloudmart.payment.service.PaymentChannelQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * R09 支付渠道可用性查询：三端收银台渠道列表的<b>唯一来源</b>。
 * 仅返回已配置凭据、支持该场景的渠道（维护中置灰标记），永不返回密钥；
 * 生产无已配置渠道时列表为空，端侧必须展示"暂不可支付"，不得内置假渠道。
 */
@RestController
@RequestMapping("/channels")
@RequiredArgsConstructor
@Tag(name = "支付渠道", description = "R09 按场景查询可用支付渠道")
public class PaymentChannelController {

    private final PaymentChannelQueryService channelQueryService;

    @GetMapping
    @Operation(summary = "查询当前场景可用支付渠道",
            description = "R09：scene=H5/MINIAPP/APP；返回渠道编码、展示名、收单动作、维护标记；"
                    + "不返回任何凭据信息。空列表=当前环境暂不可支付。")
    public ApiResponse<List<ChannelView>> listChannels(
            @Parameter(description = "支付场景：H5/MINIAPP/APP", required = true)
            @RequestParam("scene") String scene) {
        return ApiResponse.ok(channelQueryService.listChannels(PaymentScene.fromCode(scene)));
    }
}
