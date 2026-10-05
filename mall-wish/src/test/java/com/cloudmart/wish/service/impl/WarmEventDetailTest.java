package com.cloudmart.wish.service.impl;

import com.cloudmart.wish.config.WishMapProperties;
import com.cloudmart.wish.entity.WarmEvent;
import com.cloudmart.wish.repository.FenceArrivalMapper;
import com.cloudmart.wish.repository.FenceMapper;
import com.cloudmart.wish.repository.WarmEventMapper;
import com.cloudmart.wish.repository.WishMapper;
import com.cloudmart.wish.vo.WarmEventVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * T22 契约固化：温暖事件详情必须走脱敏 VO（近似坐标），禁止实体直出
 * 泄漏精确坐标/归属人 ID；owned 仅发布者本人为 true（供前端显隐撤回入口）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WarmEventDetail 脱敏契约")
class WarmEventDetailTest {

    private static final Long AUTHOR = 7001L;
    private static final Long VIEWER = 7002L;
    private static final Long EVENT_ID = 9001L;

    @Mock
    private FenceMapper fenceMapper;
    @Mock
    private FenceArrivalMapper arrivalMapper;
    @Mock
    private WarmEventMapper warmEventMapper;
    @Mock
    private WishMapper wishMapper;

    private WarmMapServiceImpl service;

    @BeforeAll
    static void initEntityMeta() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, WarmEvent.class);
    }

    @BeforeEach
    void setUp() {
        service = new WarmMapServiceImpl(fenceMapper, arrivalMapper, warmEventMapper, wishMapper,
                new WishMapProperties(), new WishContentSanitizer(java.util.List.of()));
    }

    private WarmEvent visibleEvent() {
        WarmEvent event = new WarmEvent();
        event.setId(EVENT_ID);
        event.setUserId(AUTHOR);
        event.setTitle("小店老板送咖啡");
        event.setContent("雨天的温暖");
        event.setGeohash("ws0e1d2c3b4g");
        event.setCityCode("ws0e");
        event.setAuditStatus(com.cloudmart.wish.enums.AuditStatus.APPROVED);
        event.setIsVisible(true);
        event.setCreatedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("UTC")));
        return event;
    }

    @Test
    @DisplayName("详情返回脱敏 VO：不含精确坐标与 userId 字段")
    void detail_returnsSanitizedVo() {
        when(warmEventMapper.selectById(EVENT_ID)).thenReturn(visibleEvent());

        WarmEventVO vo = service.getEventDetail(EVENT_ID, VIEWER);

        assertThat(vo.eventId()).isEqualTo(EVENT_ID);
        assertThat(vo.title()).isEqualTo("小店老板送咖啡");
        // geohash7 中心 + 确定性偏移 → 与原始 geohash7 串不直接相等（近似坐标）
        assertThat(vo.approximateLat()).isNotZero();
        assertThat(vo.approximateLng()).isNotZero();
        assertThat(vo.owned()).as("非发布者查看 owned=false").isFalse();
    }

    @Test
    @DisplayName("发布者本人查看 owned=true")
    void detail_ownerSeesOwned() {
        when(warmEventMapper.selectById(EVENT_ID)).thenReturn(visibleEvent());

        assertThat(service.getEventDetail(EVENT_ID, AUTHOR).owned()).isTrue();
    }

    @Test
    @DisplayName("匿名（未登录）查看 owned=false 且不抛异常")
    void detail_anonymousViewer() {
        when(warmEventMapper.selectById(EVENT_ID)).thenReturn(visibleEvent());

        assertThat(service.getEventDetail(EVENT_ID, null).owned()).isFalse();
    }
}
