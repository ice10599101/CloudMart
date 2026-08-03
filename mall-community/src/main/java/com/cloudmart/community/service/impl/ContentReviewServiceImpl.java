package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.SensitiveWord;
import com.cloudmart.community.repository.SensitiveWordMapper;
import com.cloudmart.community.service.ContentReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ContentReviewServiceImpl implements ContentReviewService {

    private final SensitiveWordMapper sensitiveWordMapper;
    private final ConcurrentHashMap<String, SensitiveWord> wordCache = new ConcurrentHashMap<>();
    private volatile long cacheTimestamp = 0;
    private static final long CACHE_TTL_MS = 300_000;

    public ContentReviewServiceImpl(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    @Override
    public ReviewResult reviewContent(String content) {
        if (content == null || content.isBlank()) {
            return new ReviewResult(true, false, content, null);
        }

        ensureCacheFresh();

        boolean needsManualReview = false;
        String filtered = content;

        for (SensitiveWord sw : wordCache.values()) {
            if (filtered.contains(sw.getWord())) {
                switch (sw.getLevel()) {
                    case 3:
                        return new ReviewResult(false, false, content,
                                "内容包含违规词汇，发布被拒绝");
                    case 2:
                        needsManualReview = true;
                        break;
                    case 1:
                        filtered = filtered.replace(sw.getWord(), "*".repeat(sw.getWord().length()));
                        break;
                    default:
                        break;
                }
            }
        }

        if (needsManualReview) {
            return new ReviewResult(true, true, filtered, "内容需人工审核");
        }

        return new ReviewResult(true, false, filtered, null);
    }

    @Override
    public List<SensitiveWord> listSensitiveWords(String category, int page, int size) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            wrapper.eq(SensitiveWord::getCategory, category);
        }
        wrapper.orderByDesc(SensitiveWord::getLevel);
        Page<SensitiveWord> result = sensitiveWordMapper.selectPage(new Page<>(page, size), wrapper);
        return result.getRecords();
    }

    @Override
    public SensitiveWord addSensitiveWord(String word, String category, int level) {
        if (word == null || word.isBlank()) {
            throw new BusinessException("INVALID_WORD", "敏感词不能为空");
        }

        SensitiveWord existing = sensitiveWordMapper.selectOne(
                new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getWord, word.trim()));
        if (existing != null) {
            throw new BusinessException("WORD_EXISTS", "该敏感词已存在");
        }

        SensitiveWord sw = new SensitiveWord();
        sw.setWord(word.trim());
        sw.setCategory(category != null ? category : "GENERAL");
        sw.setLevel(level);
        sensitiveWordMapper.insert(sw);

        wordCache.put(sw.getWord(), sw);
        return sw;
    }

    @Override
    public void removeSensitiveWord(Long id) {
        SensitiveWord sw = sensitiveWordMapper.selectById(id);
        if (sw != null) {
            sensitiveWordMapper.deleteById(id);
            wordCache.remove(sw.getWord());
        }
    }

    @Override
    public SensitiveWord updateSensitiveWord(Long id, String word, String category, Integer level) {
        SensitiveWord sw = sensitiveWordMapper.selectById(id);
        if (sw == null) {
            throw new BusinessException("SENSITIVE_WORD_NOT_FOUND", "敏感词不存在");
        }
        wordCache.remove(sw.getWord());
        if (word != null && !word.isBlank()) {
            sw.setWord(word.trim());
        }
        if (category != null) {
            sw.setCategory(category);
        }
        if (level != null) {
            sw.setLevel(level);
        }
        sensitiveWordMapper.updateById(sw);
        wordCache.put(sw.getWord(), sw);
        return sw;
    }

    @Override
    public void refreshCache() {
        loadCache();
        log.info("Sensitive word cache refreshed, size={}", wordCache.size());
    }

    private void ensureCacheFresh() {
        if (System.currentTimeMillis() - cacheTimestamp > CACHE_TTL_MS || wordCache.isEmpty()) {
            loadCache();
        }
    }

    private void loadCache() {
        List<SensitiveWord> words = sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<>());
        wordCache.clear();
        for (SensitiveWord sw : words) {
            wordCache.put(sw.getWord(), sw);
        }
        cacheTimestamp = System.currentTimeMillis();
    }
}
