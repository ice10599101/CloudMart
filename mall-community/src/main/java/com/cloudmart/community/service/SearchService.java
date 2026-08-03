package com.cloudmart.community.service;

import java.util.List;

public interface SearchService {

    void recordSearch(Long userId, String keyword);

    List<String> getUserSearchHistory(Long userId, int limit);

    void clearUserSearchHistory(Long userId);

    List<String> getHotSearches(int limit);
}
