package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.community.entity.HotSearch;
import com.cloudmart.community.entity.SearchHistory;
import com.cloudmart.community.repository.HotSearchMapper;
import com.cloudmart.community.repository.SearchHistoryMapper;
import com.cloudmart.community.service.CommunityCacheService;
import com.cloudmart.community.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final SearchHistoryMapper searchHistoryMapper;
    private final HotSearchMapper hotSearchMapper;
    private final CommunityCacheService communityCacheService;

    @Override
    @Transactional
    public void recordSearch(Long userId, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }

        String trimmed = keyword.trim();

        if (userId != null) {
            SearchHistory history = new SearchHistory();
            history.setUserId(userId);
            history.setKeyword(trimmed);
            searchHistoryMapper.insert(history);

            searchHistoryMapper.delete(
                    new LambdaQueryWrapper<SearchHistory>()
                            .eq(SearchHistory::getUserId, userId)
                            .eq(SearchHistory::getKeyword, trimmed)
                            .ne(SearchHistory::getId, history.getId()));
        }

        HotSearch existing = hotSearchMapper.selectOne(
                new LambdaQueryWrapper<HotSearch>().eq(HotSearch::getKeyword, trimmed));
        if (existing != null) {
            existing.setSearchCount(existing.getSearchCount() != null ? existing.getSearchCount() + 1 : 1);
            hotSearchMapper.updateById(existing);
        } else {
            HotSearch hotSearch = new HotSearch();
            hotSearch.setKeyword(trimmed);
            hotSearch.setSearchCount(1);
            hotSearchMapper.insert(hotSearch);
        }
        communityCacheService.evictHotSearches();
    }

    @Override
    public List<String> getUserSearchHistory(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<SearchHistory> histories = searchHistoryMapper.selectList(
                new LambdaQueryWrapper<SearchHistory>()
                        .eq(SearchHistory::getUserId, userId)
                        .orderByDesc(SearchHistory::getCreatedAt)
                        .last("LIMIT " + safeLimit));
        return histories.stream().map(SearchHistory::getKeyword).toList();
    }

    @Override
    @Transactional
    public void clearUserSearchHistory(Long userId) {
        searchHistoryMapper.delete(
                new LambdaQueryWrapper<SearchHistory>().eq(SearchHistory::getUserId, userId));
    }

    @Override
    public List<String> getHotSearches(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 30);

        var cached = communityCacheService.getHotSearches(safeLimit);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<HotSearch> hotSearches = hotSearchMapper.selectList(
                new LambdaQueryWrapper<HotSearch>()
                        .orderByDesc(HotSearch::getSearchCount)
                        .last("LIMIT " + safeLimit));
        List<String> result = hotSearches.stream().map(HotSearch::getKeyword).toList();
        communityCacheService.putHotSearches(safeLimit, result);
        return result;
    }
}
