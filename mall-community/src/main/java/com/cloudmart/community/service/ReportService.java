package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateReportRequest;
import com.cloudmart.community.dto.HandleReportRequest;
import com.cloudmart.community.vo.ReportVO;

public interface ReportService {

    void createReport(Long reporterId, CreateReportRequest request);

    Page<ReportVO> adminListReports(Integer status, String targetType, int page, int size);

    void handleReport(Long handlerId, Long reportId, HandleReportRequest request);
}
