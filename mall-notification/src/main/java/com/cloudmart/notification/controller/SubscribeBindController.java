package com.cloudmart.notification.controller;

import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.constant.SecurityConstants;
import com.cloudmart.notification.channel.SubscribeMessageChannel;
import com.cloudmart.notification.entity.SubscribeMessageBinding;
import com.cloudmart.notification.repository.SubscribeMessageBindingMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订阅消息授权绑定（N-1）：用户在小程序授权（requestSubscribeMessage 通过）后，
 * 以 Taro.login code 换 openid 建档（一行 = 一次发送额度）。
 */
@RestController
@RequestMapping("/notifications/subscribe")
@RequiredArgsConstructor
@Validated
@Tag(name = "订阅消息", description = "N-1：授权建档（一次授权 = 一条发送额度）")
public class SubscribeBindController {

    private final com.cloudmart.notification.channel.SubscribeMessageChannel subscribeMessageChannel;
    private final SubscribeMessageBindingMapper bindingMapper;

    public record BindRequest(
            @NotBlank(message = "code 不能为空") String code,
            @NotBlank(message = "templateKey 不能为空") String templateKey) {}

    @PostMapping("/bind")
    @Operation(summary = "授权建档", description = "Taro.login code → code2session 换 openid；一行 = 一次发送额度")
    public ApiResponse<Void> bind(
            @Parameter(hidden = true) @RequestHeader(SecurityConstants.USER_ID_HEADER) Long userId,
            @RequestBody BindRequest request) {
        String openid = subscribeMessageChannel.openidByCode(request.code());
        SubscribeMessageBinding binding = new SubscribeMessageBinding();
        binding.setUserId(userId);
        binding.setOpenid(openid);
        binding.setTemplateKey(request.templateKey());
        binding.setConsumed(false);
        bindingMapper.insert(binding);
        return ApiResponse.ok(null);
    }
}
