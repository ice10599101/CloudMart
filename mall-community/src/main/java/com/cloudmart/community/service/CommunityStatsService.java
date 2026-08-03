package com.cloudmart.community.service;

import java.util.List;
import java.util.Map;

public interface CommunityStatsService {

    Map<String, Object> getOverviewStats();

    List<Map<String, Object>> getTrendStats(int days);
}
