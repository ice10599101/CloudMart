package com.cloudmart.community.service;

import com.cloudmart.community.entity.SensitiveWord;

import java.util.List;

public interface ContentReviewService {

    ReviewResult reviewContent(String content);

    /**
     * P1-8：带媒体内容的审核（短期方案）。
     * 文本沿用敏感词三档；正文含图片/视频时强制 needsManualReview=true
     * 走人工队列（机制已支持，reviewStatus=0），杜绝图片直发即见。
     * 长期方案为接入内容安全 API 的图片机审（reviewMedia 分支预留）。
     */
    ReviewResult reviewContentWithMedia(String content, int mediaCount);

    List<SensitiveWord> listSensitiveWords(String category, int page, int size);

    SensitiveWord addSensitiveWord(String word, String category, int level);

    void removeSensitiveWord(Long id);

    SensitiveWord updateSensitiveWord(Long id, String word, String category, Integer level);

    void refreshCache();

    record ReviewResult(boolean approved, boolean needsManualReview, String filteredContent, String reason) {}
}
