package com.cloudmart.live.service.impl;

import com.cloudmart.live.dto.WebrtcSignalRequest;
import com.cloudmart.live.dto.WebrtcSignalResponse;
import com.cloudmart.live.service.WebrtcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class WebrtcServiceImpl implements WebrtcService {

    private static final Logger log = LoggerFactory.getLogger(WebrtcServiceImpl.class);
    private static final String SIGNAL_KEY_PREFIX = "live:webrtc:signal:";
    private static final String ICE_KEY_PREFIX = "live:webrtc:ice:";
    private static final long SIGNAL_TTL_SECONDS = 3600;

    private final StringRedisTemplate redisTemplate;

    public WebrtcServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void publishSignal(WebrtcSignalRequest request) {
        String key = SIGNAL_KEY_PREFIX + request.roomId() + ":" + request.role();
        String value = request.type() + "|" + request.payload();
        redisTemplate.opsForList().rightPush(key, value);
        redisTemplate.expire(key, SIGNAL_TTL_SECONDS, TimeUnit.SECONDS);
        log.debug("WebRTC signal published: roomId={}, type={}, role={}", request.roomId(), request.type(), request.role());
    }

    @Override
    public List<WebrtcSignalResponse> getSignals(Long roomId, String role) {
        String key = SIGNAL_KEY_PREFIX + roomId + ":" + role;
        List<String> rawSignals = redisTemplate.opsForList().range(key, 0, -1);

        List<WebrtcSignalResponse> responses = new ArrayList<>();
        if (rawSignals != null) {
            for (String raw : rawSignals) {
                String[] parts = raw.split("\\|", 2);
                if (parts.length == 2) {
                    responses.add(new WebrtcSignalResponse(parts[0], parts[1], role));
                }
            }
        }
        return responses;
    }

    @Override
    public void publishIceCandidate(WebrtcSignalRequest request) {
        String key = ICE_KEY_PREFIX + request.roomId() + ":" + request.role();
        redisTemplate.opsForList().rightPush(key, request.payload());
        redisTemplate.expire(key, SIGNAL_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public List<String> getIceCandidates(Long roomId, String role) {
        String key = ICE_KEY_PREFIX + roomId + ":" + role;
        List<String> candidates = redisTemplate.opsForList().range(key, 0, -1);
        return candidates != null ? candidates : List.of();
    }

    @Override
    public void clearSignals(Long roomId) {
        redisTemplate.delete(SIGNAL_KEY_PREFIX + roomId + ":HOST");
        redisTemplate.delete(SIGNAL_KEY_PREFIX + roomId + ":VIEWER");
        redisTemplate.delete(ICE_KEY_PREFIX + roomId + ":HOST");
        redisTemplate.delete(ICE_KEY_PREFIX + roomId + ":VIEWER");
    }
}
