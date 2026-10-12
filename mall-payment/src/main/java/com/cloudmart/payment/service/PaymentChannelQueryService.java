package com.cloudmart.payment.service;

import com.cloudmart.payment.channel.ChannelRegistry;
import com.cloudmart.payment.channel.PaymentAction;
import com.cloudmart.payment.channel.PaymentChannelCatalog;
import com.cloudmart.payment.channel.PaymentScene;
import com.cloudmart.payment.dto.ChannelView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * R09 渠道可用性查询：白名单（凭据已配置）∩ 渠道目录（场景能力）− 不支持场景，
 * 维护中渠道保留并标记（端侧置灰）。生产无已配置渠道时返回空列表——收银台必须
 * 展示"暂不可支付"，禁止端上写死 ALIPAY/WECHAT/BANK_CARD 假按钮。
 */
@Slf4j
@Service
public class PaymentChannelQueryService {

    private final ChannelRegistry channelRegistry;
    private final PaymentChannelCatalog catalog;
    private final Set<String> maintenanceChannels;

    public PaymentChannelQueryService(ChannelRegistry channelRegistry,
                                      PaymentChannelCatalog catalog,
                                      @Value("${payment.channels.maintenance:}") String maintenanceCsv) {
        this.channelRegistry = channelRegistry;
        this.catalog = catalog;
        String[] maintenanceTokens = maintenanceCsv == null ? new String[0] : maintenanceCsv.split(",");
        this.maintenanceChannels = Arrays.stream(maintenanceTokens)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 列出指定场景下可展示的渠道（维护中置灰；不支持该场景的渠道不出现）。 */
    public List<ChannelView> listChannels(PaymentScene scene) {
        return channelRegistry.availableChannelCodes().stream()
                .map(catalog::find)
                .flatMap(Optional::stream)
                .filter(descriptor -> descriptor.supports(scene))
                .map(descriptor -> new ChannelView(
                        descriptor.code(),
                        descriptor.displayName(),
                        descriptor.action(scene).name(),
                        scene.name(),
                        maintenanceChannels.contains(descriptor.code())))
                .toList();
    }

    /** 渠道在指定场景的收单动作；未知/不支持返回 empty（创建尝试防御性校验用）。 */
    public Optional<PaymentAction> actionOf(String channel, PaymentScene scene) {
        return catalog.find(channel)
                .filter(descriptor -> descriptor.supports(scene))
                .map(descriptor -> descriptor.action(scene));
    }
}
