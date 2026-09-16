package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.entity.BrowseHistory;
import com.cloudmart.community.repository.BrowseHistoryMapper;
import com.cloudmart.community.service.BrowseHistoryService;
import com.cloudmart.community.vo.BrowseHistoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BrowseHistoryServiceImpl implements BrowseHistoryService {

    /** 单用户足迹保留上限，防止足迹表无限膨胀。 */
    private static final int MAX_HISTORY_PER_USER = 100;

    /** 超出上限的冗余阈值：超过后才触发一次裁剪，避免每次上报都执行裁剪 SQL。 */
    private static final int PRUNE_THRESHOLD = MAX_HISTORY_PER_USER + 20;

    private final BrowseHistoryMapper browseHistoryMapper;

    @Override
    @Transactional
    public void recordBrowse(Long userId, String targetType, Long targetId, String title, String cover) {
        BrowseHistory history = new BrowseHistory();
        history.setUserId(userId);
        history.setTargetType(targetType);
        history.setTargetId(targetId);
        history.setTitle(title == null ? "" : title.trim());
        history.setCover(cover == null ? "" : cover.trim());
        browseHistoryMapper.upsert(history);

        long total = browseHistoryMapper.countByUser(userId);
        if (total > PRUNE_THRESHOLD) {
            browseHistoryMapper.pruneBeyondLimit(userId, MAX_HISTORY_PER_USER);
        }
    }

    @Override
    public Page<BrowseHistoryVO> listMyHistory(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        long total = browseHistoryMapper.countByUser(userId);
        List<BrowseHistoryVO> records = browseHistoryMapper
                .selectPageByUser(userId, (long) (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toVO)
                .toList();

        Page<BrowseHistoryVO> result = new Page<>(safePage, safeSize);
        result.setTotal(total);
        result.setRecords(records);
        return result;
    }

    private BrowseHistoryVO toVO(BrowseHistory entity) {
        return new BrowseHistoryVO(
                entity.getId(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getTitle(),
                entity.getCover(),
                entity.getViewedAt());
    }
}