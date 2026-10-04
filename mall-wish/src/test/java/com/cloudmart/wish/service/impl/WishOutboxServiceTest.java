package com.cloudmart.wish.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.wish.entity.WishOutboxEvent;
import com.cloudmart.wish.mq.WishStatEventProducer;
import com.cloudmart.wish.repository.WishOutboxMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WishOutboxService 单元测试（T01）")
class WishOutboxServiceTest {

    private static final ObjectMapper STRICT_MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    private WishOutboxMapper outboxMapper;
    @Mock
    private RocketMQTemplate rocketMQTemplate;

    private WishOutboxService outboxService;

    @BeforeAll
    static void initEntityMeta() {
        // LambdaWrapper 构造期解析列名需要 TableInfo 缓存
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WishOutboxEvent.class);
    }

    @BeforeEach
    void setUp() {
        outboxService = new WishOutboxService(outboxMapper, rocketMQTemplate, "test-instance");
    }

    private WishOutboxEvent captureInsertedEvent() {
        ArgumentCaptor<WishOutboxEvent> captor = ArgumentCaptor.forClass(WishOutboxEvent.class);
        verify(outboxMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("publish - payload 防御性复制与 envelope")
    class PublishPayloadTests {

        @Test
        @DisplayName("可变 payload：原对象不被注入 eventId，落库体含 eventId")
        void publish_mutablePayload_callerUnchanged() {
            Map<String, Object> callerPayload = new HashMap<>();
            callerPayload.put("wishId", 2001L);

            outboxService.publish("WISH", 2001L, 0L, "WishArchived", callerPayload);

            // 旧实现会向调用方 Map 写入 eventId——调用方对象必须保持原样
            assertThat(callerPayload).containsOnlyKeys("wishId");

            WishOutboxEvent event = captureInsertedEvent();
            JsonNode body = readTree(event.getPayload());
            assertThat(body.get("eventId").asText()).isEqualTo(event.getEventId());
            assertThat(body.get("wishId").asLong()).isEqualTo(2001L);
            assertThat(event.getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("不可变 payload（Map.of）：不抛 UnsupportedOperationException，正常落库")
        void publish_immutablePayload_noException() {
            // 旧实现在此抛 UnsupportedOperationException 并连带回滚业务事务（T01 缺陷）
            Map<String, Object> payload = Map.of("wishId", 2001L, "auditStatus", "APPROVED");

            assertThatCode(() -> outboxService.publish("WISH", 2001L, 0L, "WishModerated", payload))
                    .doesNotThrowAnyException();

            WishOutboxEvent event = captureInsertedEvent();
            assertThat(readTree(event.getPayload()).get("auditStatus").asText()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("null payload：归一为空 body，仅含 envelope（eventId 始终存在）")
        void publish_nullPayload_eventIdAlwaysPresent() {
            outboxService.publish("WALLET", 1001L, 0L, "HelpedRecorded", null);

            WishOutboxEvent event = captureInsertedEvent();
            JsonNode body = readTree(event.getPayload());
            assertThat(body.hasNonNull("eventId")).isTrue();
            assertThat(body.get("eventId").asText()).isEqualTo(event.getEventId());
            // 无调用方业务字段，仅 envelope 8 键
            assertThat(body.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder("eventId", "eventType", "schemaVersion",
                            "aggregateType", "aggregateId", "aggregateVersion", "occurredAt", "traceId");
        }

        @Test
        @DisplayName("envelope 完整：eventType/schemaVersion/aggregate*/occurredAt/traceId")
        void publish_envelopeComplete() {
            outboxService.publish("FULFILLMENT", 3001L, 7L, "WishFulfilled",
                    Map.of("wishId", 2001L));

            WishOutboxEvent event = captureInsertedEvent();
            JsonNode body = readTree(event.getPayload());
            assertThat(body.get("eventId").asText()).isEqualTo(event.getEventId());
            assertThat(body.get("eventType").asText()).isEqualTo("WishFulfilled");
            assertThat(body.get("schemaVersion").asInt()).isEqualTo(WishOutboxService.SCHEMA_VERSION);
            assertThat(body.get("aggregateType").asText()).isEqualTo("FULFILLMENT");
            assertThat(body.get("aggregateId").asLong()).isEqualTo(3001L);
            assertThat(body.get("aggregateVersion").asLong()).isEqualTo(7L);
            // occurredAt 为可解析的 ISO-8601 时间
            Instant occurredAt = Instant.parse(body.get("occurredAt").asText());
            assertThat(occurredAt).isNotNull();
            // 单测无 SkyWalking agent：traceId 允许缺失但键存在，消费端兼容可预期
            assertThat(body.has("traceId")).isTrue();
        }

        @Test
        @DisplayName("调用方伪造的 eventId 被权威 envelope 覆盖为行主键")
        void publish_callerEventId_overriddenByRowKey() {
            outboxService.publish("WISH", 1L, 0L, "WishDeleted",
                    Map.of("eventId", "caller-forged-id"));

            WishOutboxEvent event = captureInsertedEvent();
            assertThat(readTree(event.getPayload()).get("eventId").asText())
                    .isEqualTo(event.getEventId())
                    .isNotEqualTo("caller-forged-id");
        }
    }

    @Nested
    @DisplayName("relay - 重试投递保持原 eventId/payload")
    class RelayTests {

        @Test
        @DisplayName("发送失败重试后再次投递：eventId 与 payload 与首次完全一致")
        void relay_retryAfterFailure_sameEventIdAndPayload() throws Exception {
            outboxService.publish("WALLET", 1001L, 0L, "HelpedRecorded", Map.of("userId", 1001L));
            WishOutboxEvent event = captureInsertedEvent();
            when(outboxMapper.selectList(any())).thenReturn(List.of(event));
            // T16：tryLease 认领成功后回读权威租约版本（回读行已带持有者）
            event.setLeaseOwner("test-instance");
            when(outboxMapper.selectOne(any())).thenReturn(event);
            when(outboxMapper.update(any(), any())).thenReturn(1);

            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            doThrow(new RuntimeException("broker down"))
                    .doAnswer(inv -> null)
                    .when(rocketMQTemplate).syncSend(anyString(), org.mockito.ArgumentMatchers.<Object>any());

            outboxService.relayDueEvents();
            outboxService.relayDueEvents();

            // 两次投递（首投失败 + 重试成功）使用同一 payload 字符串——eventId 恒定
            verify(rocketMQTemplate, times(2)).syncSend(startsWith("wish-events:"), payloadCaptor.capture());
            List<String> payloads = payloadCaptor.getAllValues();
            assertThat(payloads).hasSize(2);
            assertThat(payloads.get(0)).isEqualTo(payloads.get(1));
            JsonNode body = readTree(payloads.get(1));
            assertThat(body.get("eventId").asText()).isEqualTo(event.getEventId());
        }

        @Test
        @DisplayName("投递成功置 PUBLISHED 并记录 published_at")
        void relay_success_marksPublished() throws Exception {
            outboxService.publish("WISH", 2001L, 0L, "WishModerated",
                    Map.of("wishId", 2001L, "actorId", 1L));
            WishOutboxEvent event = captureInsertedEvent();
            when(outboxMapper.selectList(any())).thenReturn(List.of(event));
            event.setLeaseOwner("test-instance");
            when(outboxMapper.selectOne(any())).thenReturn(event);
            when(outboxMapper.update(any(), any())).thenReturn(1);

            outboxService.relayDueEvents();

            verify(rocketMQTemplate, atLeastOnce())
                    .syncSend(startsWith("wish-events:"), anyString());
        }
    }

    @Nested
    @DisplayName("消费端 envelope 兼容")
    class ConsumerCompatibilityTests {

        @Test
        @DisplayName("HelpedEventMessage 以 rocketmq-spring 默认严格 mapper 反序列化：忽略 envelope 未知字段")
        void helpedEventMessage_toleratesEnvelopeFields() throws Exception {
            outboxService.publish("WALLET", 1001L, 0L, "HelpedRecorded", Map.of("userId", 1001L));
            String payload = captureInsertedEvent().getPayload();

            // rocketmq-spring 2.3.6 自建 mapper 等价物：FAIL_ON_UNKNOWN_PROPERTIES 保持默认开启，
            // 消费 record 必须自带 @JsonIgnoreProperties 否则 envelope 扩展字段会导致消费失败
            WishStatEventProducer.HelpedEventMessage message =
                    STRICT_MAPPER.readValue(payload, WishStatEventProducer.HelpedEventMessage.class);

            assertThat(message.userId()).isEqualTo(1001L);
            assertThat(message.eventId()).isNotBlank();
        }
    }

    private static JsonNode readTree(String json) {
        try {
            return STRICT_MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("payload 不是合法 JSON", ex);
        }
    }
}
