package com.cloudmart.software.service;

import java.util.Map;

/**
 * 运营统计（方案 10.7，P1，权限 software:stats）：发布数、审核时长、
 * 获取链接次数／人数、收藏数、新评分、待处理反馈/举报；
 * 口径与时间范围随响应返回，不显示外部下载完成率。
 */
public interface AdminSoftwareStatisticsService {

    Map<String, Object> overview();
}
