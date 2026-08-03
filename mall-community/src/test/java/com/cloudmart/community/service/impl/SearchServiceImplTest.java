package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cloudmart.community.entity.HotSearch;
import com.cloudmart.community.entity.SearchHistory;
import com.cloudmart.community.repository.HotSearchMapper;
import com.cloudmart.community.repository.SearchHistoryMapper;
import com.cloudmart.community.service.CommunityCacheService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceImplTest {

    @Mock
    private SearchHistoryMapper searchHistoryMapper;

    @Mock
    private HotSearchMapper hotSearchMapper;

    @Mock
    private CommunityCacheService communityCacheService;

    private SearchServiceImpl searchService;

    private static final Long USER_ID = 1L;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant historyAssistant = new MapperBuilderAssistant(configuration, "");
        historyAssistant.setCurrentNamespace("com.cloudmart.community.repository.SearchHistoryMapper");
        TableInfoHelper.initTableInfo(historyAssistant, SearchHistory.class);
        MapperBuilderAssistant hotSearchAssistant = new MapperBuilderAssistant(configuration, "");
        hotSearchAssistant.setCurrentNamespace("com.cloudmart.community.repository.HotSearchMapper");
        TableInfoHelper.initTableInfo(hotSearchAssistant, HotSearch.class);
    }

    @BeforeEach
    void setUp() {
        searchService = new SearchServiceImpl(searchHistoryMapper, hotSearchMapper, communityCacheService);
    }

    private HotSearch buildHotSearch(String keyword, int count) {
        HotSearch hotSearch = new HotSearch();
        hotSearch.setId(1L);
        hotSearch.setKeyword(keyword);
        hotSearch.setSearchCount(count);
        return hotSearch;
    }

    private SearchHistory buildSearchHistory(String keyword) {
        SearchHistory history = new SearchHistory();
        history.setId(1L);
        history.setUserId(USER_ID);
        history.setKeyword(keyword);
        return history;
    }

    @Nested
    @DisplayName("recordSearch")
    class RecordSearchTests {

        @Test
        @DisplayName("should record search history for logged-in user and update hot search count")
        void recordSearch_authenticatedUser() {
            HotSearch existingHotSearch = buildHotSearch("Java", 5);
            when(searchHistoryMapper.insert(any(SearchHistory.class))).thenAnswer(invocation -> {
                SearchHistory history = invocation.getArgument(0);
                history.setId(1L);
                return 1;
            });
            when(hotSearchMapper.selectOne(any())).thenReturn(existingHotSearch);

            searchService.recordSearch(USER_ID, "Java");

            verify(searchHistoryMapper).insert(any(SearchHistory.class));
            verify(searchHistoryMapper).delete(any());
            assertThat(existingHotSearch.getSearchCount()).isEqualTo(6);
            verify(hotSearchMapper).updateById(existingHotSearch);
            verify(communityCacheService).evictHotSearches();
        }

        @Test
        @DisplayName("should create new hot search when keyword not exists")
        void recordSearch_newHotSearch() {
            when(searchHistoryMapper.insert(any(SearchHistory.class))).thenAnswer(invocation -> {
                SearchHistory history = invocation.getArgument(0);
                history.setId(1L);
                return 1;
            });
            when(hotSearchMapper.selectOne(any())).thenReturn(null);

            searchService.recordSearch(USER_ID, "Spring");

            verify(hotSearchMapper).insert(any(HotSearch.class));
            verify(communityCacheService).evictHotSearches();
        }

        @Test
        @DisplayName("should skip recording when keyword is blank")
        void recordSearch_blankKeyword() {
            searchService.recordSearch(USER_ID, "   ");

            verify(searchHistoryMapper, never()).insert(any(SearchHistory.class));
            verify(hotSearchMapper, never()).insert(any(HotSearch.class));
            verify(communityCacheService, never()).evictHotSearches();
        }

        @Test
        @DisplayName("should skip recording when keyword is null")
        void recordSearch_nullKeyword() {
            searchService.recordSearch(USER_ID, null);

            verify(searchHistoryMapper, never()).insert(any(SearchHistory.class));
            verify(hotSearchMapper, never()).insert(any(HotSearch.class));
        }

        @Test
        @DisplayName("should not save search history for anonymous user but update hot search")
        void recordSearch_anonymousUser() {
            HotSearch existingHotSearch = buildHotSearch("Java", 3);
            when(hotSearchMapper.selectOne(any())).thenReturn(existingHotSearch);

            searchService.recordSearch(null, "Java");

            verify(searchHistoryMapper, never()).insert(any(SearchHistory.class));
            assertThat(existingHotSearch.getSearchCount()).isEqualTo(4);
            verify(hotSearchMapper).updateById(existingHotSearch);
            verify(communityCacheService).evictHotSearches();
        }

        @Test
        @DisplayName("should handle null search count in existing hot search")
        void recordSearch_nullSearchCount() {
            HotSearch existingHotSearch = buildHotSearch("Java", 0);
            existingHotSearch.setSearchCount(null);
            when(searchHistoryMapper.insert(any(SearchHistory.class))).thenAnswer(invocation -> {
                SearchHistory history = invocation.getArgument(0);
                history.setId(1L);
                return 1;
            });
            when(hotSearchMapper.selectOne(any())).thenReturn(existingHotSearch);

            searchService.recordSearch(USER_ID, "Java");

            assertThat(existingHotSearch.getSearchCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getUserSearchHistory")
    class GetUserSearchHistoryTests {

        @Test
        @DisplayName("should return user search history keywords")
        void getUserSearchHistory_success() {
            SearchHistory history1 = buildSearchHistory("Java");
            SearchHistory history2 = buildSearchHistory("Spring");
            when(searchHistoryMapper.selectList(any())).thenReturn(List.of(history1, history2));

            List<String> result = searchService.getUserSearchHistory(USER_ID, 10);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactly("Java", "Spring");
        }

        @Test
        @DisplayName("should return empty list when no history")
        void getUserSearchHistory_empty() {
            when(searchHistoryMapper.selectList(any())).thenReturn(List.of());

            List<String> result = searchService.getUserSearchHistory(USER_ID, 10);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("clearUserSearchHistory")
    class ClearUserSearchHistoryTests {

        @Test
        @DisplayName("should delete all search history for user")
        void clearUserSearchHistory_success() {
            searchService.clearUserSearchHistory(USER_ID);

            verify(searchHistoryMapper).delete(any());
        }
    }

    @Nested
    @DisplayName("getHotSearches")
    class GetHotSearchesTests {

        @Test
        @DisplayName("should return cached hot searches when available")
        void getHotSearches_cached() {
            when(communityCacheService.getHotSearches(anyInt())).thenReturn(Optional.of(List.of("Java", "Spring")));

            List<String> result = searchService.getHotSearches(10);

            assertThat(result).containsExactly("Java", "Spring");
            verify(hotSearchMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("should query DB and cache result when not cached")
        void getHotSearches_notCached() {
            HotSearch hotSearch1 = buildHotSearch("Java", 100);
            HotSearch hotSearch2 = buildHotSearch("Spring", 80);
            when(communityCacheService.getHotSearches(anyInt())).thenReturn(Optional.empty());
            when(hotSearchMapper.selectList(any())).thenReturn(List.of(hotSearch1, hotSearch2));

            List<String> result = searchService.getHotSearches(10);

            assertThat(result).containsExactly("Java", "Spring");
            verify(hotSearchMapper).selectList(any());
            verify(communityCacheService).putHotSearches(anyInt(), any());
        }

        @Test
        @DisplayName("should clamp limit to safe bounds")
        void getHotSearches_clampLimit() {
            when(communityCacheService.getHotSearches(anyInt())).thenReturn(Optional.of(List.of()));

            searchService.getHotSearches(100);

            verify(communityCacheService).getHotSearches(30);
        }
    }
}
