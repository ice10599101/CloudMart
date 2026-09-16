package com.cloudmart.community.service.impl;

import com.cloudmart.community.entity.BrowseHistory;
import com.cloudmart.community.repository.BrowseHistoryMapper;
import com.cloudmart.community.vo.BrowseHistoryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrowseHistoryServiceImplTest {

    @Mock
    private BrowseHistoryMapper browseHistoryMapper;

    private BrowseHistoryServiceImpl browseHistoryService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        browseHistoryService = new BrowseHistoryServiceImpl(browseHistoryMapper);
    }

    private BrowseHistory buildEntity(Long id, String targetType, Long targetId, String title,
                                      String cover, LocalDateTime viewedAt) {
        BrowseHistory history = new BrowseHistory();
        history.setId(id);
        history.setUserId(USER_ID);
        history.setTargetType(targetType);
        history.setTargetId(targetId);
        history.setTitle(title);
        history.setCover(cover);
        history.setViewedAt(viewedAt);
        return history;
    }

    @Nested
    @DisplayName("recordBrowse")
    class RecordBrowseTests {

        @Test
        @DisplayName("正常上报：写入并规范化 title/cover，未超阈值不裁剪")
        void recordBrowse_ShouldUpsertNormalizedValues() {
            when(browseHistoryMapper.countByUser(USER_ID)).thenReturn(10L);

            browseHistoryService.recordBrowse(USER_ID, "PRODUCT", 88L, "  华为手机  ", "  http://x/1.jpg  ");

            ArgumentCaptor<BrowseHistory> captor = ArgumentCaptor.forClass(BrowseHistory.class);
            verify(browseHistoryMapper).upsert(captor.capture());
            BrowseHistory saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getTargetType()).isEqualTo("PRODUCT");
            assertThat(saved.getTargetId()).isEqualTo(88L);
            assertThat(saved.getTitle()).isEqualTo("华为手机");
            assertThat(saved.getCover()).isEqualTo("http://x/1.jpg");
            verify(browseHistoryMapper, never()).pruneBeyondLimit(anyLong(), anyInt());
        }

        @Test
        @DisplayName("title/cover 为空：规范化为空字符串")
        void recordBrowse_WithNullSnapshots_ShouldNormalizeToEmpty() {
            when(browseHistoryMapper.countByUser(USER_ID)).thenReturn(5L);

            browseHistoryService.recordBrowse(USER_ID, "WISH", 9L, null, null);

            ArgumentCaptor<BrowseHistory> captor = ArgumentCaptor.forClass(BrowseHistory.class);
            verify(browseHistoryMapper).upsert(captor.capture());
            assertThat(captor.getValue().getTitle()).isEmpty();
            assertThat(captor.getValue().getCover()).isEmpty();
        }

        @Test
        @DisplayName("超过冗余阈值：裁剪到保留上限")
        void recordBrowse_WhenOverThreshold_ShouldPrune() {
            when(browseHistoryMapper.countByUser(USER_ID)).thenReturn(130L);

            browseHistoryService.recordBrowse(USER_ID, "POST", 7L, "帖", "");

            verify(browseHistoryMapper).pruneBeyondLimit(USER_ID, 100);
        }
    }

    @Nested
    @DisplayName("listMyHistory")
    class ListMyHistoryTests {

        private static final LocalDateTime VIEWED_AT = LocalDateTime.of(2026, 9, 17, 10, 0);

        @Test
        @DisplayName("正常分页：返回 VO 列表与总数")
        void listMyHistory_ShouldReturnPage() {
            BrowseHistory entity = buildEntity(5L, "PRODUCT", 88L, "华为手机", "http://x/1.jpg", VIEWED_AT);
            when(browseHistoryMapper.countByUser(USER_ID)).thenReturn(1L);
            when(browseHistoryMapper.selectPageByUser(eq(USER_ID), eq(0L), eq(20L)))
                    .thenReturn(List.of(entity));

            var page = browseHistoryService.listMyHistory(USER_ID, 1, 20);

            assertThat(page.getTotal()).isEqualTo(1L);
            assertThat(page.getRecords()).hasSize(1);
            BrowseHistoryVO vo = page.getRecords().getFirst();
            assertThat(vo.id()).isEqualTo(5L);
            assertThat(vo.targetType()).isEqualTo("PRODUCT");
            assertThat(vo.targetId()).isEqualTo(88L);
            assertThat(vo.title()).isEqualTo("华为手机");
            assertThat(vo.viewedAt()).isEqualTo(VIEWED_AT);
        }

        @Test
        @DisplayName("非法分页参数：收敛到安全边界")
        void listMyHistory_WithInvalidPageParams_ShouldClamp() {
            when(browseHistoryMapper.countByUser(USER_ID)).thenReturn(0L);
            when(browseHistoryMapper.selectPageByUser(USER_ID, 0L, 1L)).thenReturn(List.of());

            var page = browseHistoryService.listMyHistory(USER_ID, -2, 0);

            assertThat(page.getCurrent()).isEqualTo(1);
            assertThat(page.getSize()).isEqualTo(1);
            verify(browseHistoryMapper).selectPageByUser(USER_ID, 0L, 1L);
        }

        @Test
        @DisplayName("超大 size：截断到 50")
        void listMyHistory_WithHugeSize_ShouldCapAt50() {
            when(browseHistoryMapper.countByUser(USER_ID)).thenReturn(0L);
            when(browseHistoryMapper.selectPageByUser(USER_ID, 0L, 50L)).thenReturn(List.of());

            var page = browseHistoryService.listMyHistory(USER_ID, 1, 500);

            assertThat(page.getSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("第二页 offset 计算正确")
        void listMyHistory_SecondPage_ShouldComputeOffset() {
            when(browseHistoryMapper.countByUser(USER_ID)).thenReturn(0L);
            when(browseHistoryMapper.selectPageByUser(USER_ID, 40L, 20L)).thenReturn(List.of());

            browseHistoryService.listMyHistory(USER_ID, 3, 20);

            verify(browseHistoryMapper).selectPageByUser(eq(USER_ID), eq(40L), eq(20L));
        }

        @Test
        @DisplayName("无足迹：返回空列表")
        void listMyHistory_Empty_ShouldReturnEmptyList() {
            when(browseHistoryMapper.countByUser(USER_ID)).thenReturn(0L);
            when(browseHistoryMapper.selectPageByUser(USER_ID, 0L, 10L)).thenReturn(List.of());

            var page = browseHistoryService.listMyHistory(USER_ID, 1, 10);

            assertThat(page.getTotal()).isZero();
            assertThat(page.getRecords()).isEmpty();
        }
    }
}