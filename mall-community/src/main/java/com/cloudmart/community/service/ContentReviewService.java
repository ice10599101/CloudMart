package com.cloudmart.community.service;

import com.cloudmart.community.entity.SensitiveWord;

import java.util.List;

public interface ContentReviewService {

    ReviewResult reviewContent(String content);

    List<SensitiveWord> listSensitiveWords(String category, int page, int size);

    SensitiveWord addSensitiveWord(String word, String category, int level);

    void removeSensitiveWord(Long id);

    SensitiveWord updateSensitiveWord(Long id, String word, String category, Integer level);

    void refreshCache();

    record ReviewResult(boolean approved, boolean needsManualReview, String filteredContent, String reason) {}
}
