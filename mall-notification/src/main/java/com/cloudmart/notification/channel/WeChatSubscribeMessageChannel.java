package com.cloudmart.notification.channel;

import com.cloudmart.notification.entity.SubscribeMessageBinding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * N-1 微信小程序订阅消息通道（provider=wechat 时启用）。
 *
 * <p>发送资格：Taro.requestSubscribeMessage 授权一次 = 一条额度（subscribe_message_binding
 * 一行）；用户先经 /notifications/subscribe/bind 以 Taro.login code 换 openid 建档。</p>
 *
 * <p>access_token 以小程序 appid+secret 获取并内存缓存（7200s 官方有效期，提前 5 分钟刷新）；
 * 发送失败 fail-open（返回 false，不阻断站内通知）；43101（用户拒收/未授权）消耗该条额度。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.subscribe-message.provider", havingValue = "wechat")
public class WeChatSubscribeMessageChannel implements SubscribeMessageChannel {

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s";
    private static final String SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=%s";
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 300;

    /** 业务模板键 → 微信模板 ID（Nacos 可配；ANNIVERSARY=宠物祝福提醒） */
    private final Map<String, String> templates;
    private final String appid;
    private final String secret;
    private final RestClient restClient = RestClient.create();

    private final com.cloudmart.notification.repository.SubscribeMessageBindingMapper bindingMapper;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public WeChatSubscribeMessageChannel(
            @Value("${notification.subscribe-message.wechat.appid}") String appid,
            @Value("${notification.subscribe-message.wechat.secret}") String secret,
            @Value("${notification.subscribe-message.templates.anniversary:}") String anniversaryTemplateId,
            com.cloudmart.notification.repository.SubscribeMessageBindingMapper bindingMapper) {
        this.appid = appid;
        this.secret = secret;
        this.templates = Map.of("ANNIVERSARY", anniversaryTemplateId == null ? "" : anniversaryTemplateId);
        this.bindingMapper = bindingMapper;
    }

    @Override
    public boolean send(Long userId, String templateKey, Map<String, String> data) {
        String templateId = templates.getOrDefault(templateKey, "");
        if (templateId.isBlank()) {
            log.warn("N-1 模板未配置，跳过微信发送: templateKey={}", templateKey);
            return false;
        }
        SubscribeMessageBinding binding = oldestQuota(userId, templateKey);
        if (binding == null) {
            log.info("N-1 用户无可用订阅额度: userId={}, templateKey={}", userId, templateKey);
            return false;
        }
        try {
            String openid = binding.getOpenid();
            Map<String, Object> body = Map.of(
                    "touser", openid,
                    "template_id", templateId,
                    "page", "pages/pet/index",
                    // thing1=宠物名称、thing2=祝福语（模板关键词顺序）
                    "data", Map.of(
                            "thing1", Map.of("value", truncate(data.getOrDefault("title", ""), 20)),
                            "thing2", Map.of("value", truncate(data.getOrDefault("content", ""), 20))));
            String response = restClient.post()
                    .uri(String.format(SEND_URL, accessToken()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            int errcode = parseErrcode(response);
            if (errcode == 0) {
                consume(binding);
                log.info("N-1 微信订阅消息已发送: userId={}, templateKey={}", userId, templateKey);
                return true;
            }
            // 43101 = 用户拒收/额度耗尽：消耗该条并告警；其余错误保留额度（可重试）
            log.warn("N-1 微信订阅消息发送失败: userId={}, errcode={}, resp={}", userId, errcode, response);
            if (errcode == 43101) {
                consume(binding);
            }
            return false;
        } catch (Exception e) {
            log.warn("N-1 微信订阅消息异常（fail-open 忽略）: userId={}, err={}", userId, e.getMessage());
            return false;
        }
    }

    /** code2session：Taro.login code → openid（绑定建档用） */
    public String openidByCode(String jsCode) {
        Map<String, Object> resp = restClient.get()
                .uri(String.format("https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                        appid, secret, jsCode))
                .retrieve()
                .body(Map.class);
        Object openid = resp == null ? null : resp.get("openid");
        Object errcode = resp == null ? null : resp.get("errcode");
        if (openid == null) {
            throw new com.cloudmart.common.exception.BusinessException(
                    "WECHAT_LOGIN_FAILED", "微信登录态获取失败: " + errcode);
        }
        return String.valueOf(openid);
    }

    private SubscribeMessageBinding oldestQuota(Long userId, String templateKey) {
        List<SubscribeMessageBinding> rows = bindingMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SubscribeMessageBinding>()
                        .eq(SubscribeMessageBinding::getUserId, userId)
                        .eq(SubscribeMessageBinding::getTemplateKey, templateKey)
                        .eq(SubscribeMessageBinding::getConsumed, false)
                        .orderByAsc(SubscribeMessageBinding::getId)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void consume(SubscribeMessageBinding binding) {
        bindingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SubscribeMessageBinding>()
                .eq(SubscribeMessageBinding::getId, binding.getId())
                .eq(SubscribeMessageBinding::getConsumed, false)
                .set(SubscribeMessageBinding::getConsumed, true)
                .set(SubscribeMessageBinding::getConsumedAt, java.time.LocalDateTime.now()));
    }

    private synchronized String accessToken() {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedAccessToken;
        }
        Map<String, Object> resp = restClient.get()
                .uri(String.format(TOKEN_URL, appid, secret))
                .retrieve()
                .body(Map.class);
        Object token = resp == null ? null : resp.get("access_token");
        Object expiresIn = resp == null ? null : resp.get("expires_in");
        if (token == null) {
            throw new IllegalStateException("微信 access_token 获取失败: " + resp);
        }
        cachedAccessToken = String.valueOf(token);
        long seconds = expiresIn instanceof Number n ? n.longValue() : 7200L;
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(60, seconds - TOKEN_REFRESH_MARGIN_SECONDS));
        return cachedAccessToken;
    }

    /** thing 类型限 20 字符且不允许换行/emoji 等特殊字符，超限截断并剥离非法字符 */
    private String truncate(String raw, int max) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("[\\p{So}\\p{Cn}\\r\\n]", "").trim();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private int parseErrcode(String response) {
        try {
            Map<String, Object> map = objectMapper().readValue(response, Map.class);
            Object errcode = map.get("errcode");
            return errcode instanceof Number n ? n.intValue() : (errcode != null ? Integer.parseInt(String.valueOf(errcode)) : -1);
        } catch (Exception e) {
            return -1;
        }
    }

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper();
    }
}
