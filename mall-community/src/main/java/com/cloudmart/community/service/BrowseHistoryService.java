package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.vo.BrowseHistoryVO;

public interface BrowseHistoryService {

    /** 记录一次浏览足迹；同一对象重复浏览仅刷新时间与快照。 */
    void recordBrowse(Long userId, String targetType, Long targetId, String title, String cover);

    /** 分页查询当前用户的浏览足迹（按最近浏览时间倒序）。 */
    Page<BrowseHistoryVO> listMyHistory(Long userId, int page, int size);
}