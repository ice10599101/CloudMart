package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateReportRequest;
import com.cloudmart.community.dto.HandleReportRequest;
import com.cloudmart.community.entity.Post;
import com.cloudmart.community.entity.PostComment;
import com.cloudmart.community.entity.Report;
import com.cloudmart.community.repository.PostCommentMapper;
import com.cloudmart.community.repository.PostMapper;
import com.cloudmart.community.repository.ReportMapper;
import com.cloudmart.community.service.ReportService;
import com.cloudmart.community.vo.ReportVO;
import com.cloudmart.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;
    private final ObjectMapper objectMapper;

    public ReportServiceImpl(ReportMapper reportMapper,
                             PostMapper postMapper,
                             PostCommentMapper postCommentMapper,
                             ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.postMapper = postMapper;
        this.postCommentMapper = postCommentMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void createReport(Long reporterId, CreateReportRequest request) {
        Report report = new Report();
        report.setReporterId(reporterId);
        report.setTargetType(request.targetType());
        report.setTargetId(request.targetId());
        report.setReason(request.reason());
        report.setDescription(request.description());
        report.setImages(serializeImages(request.images()));
        report.setStatus(0);
        reportMapper.insert(report);
    }

    @Override
    public Page<ReportVO> adminListReports(Integer status, String targetType, int page, int size) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();

        if (status != null) {
            wrapper.eq(Report::getStatus, status);
        }
        if (targetType != null && !targetType.isBlank()) {
            wrapper.eq(Report::getTargetType, targetType);
        }
        wrapper.orderByDesc(Report::getCreatedAt);

        Page<Report> reportPage = reportMapper.selectPage(new Page<>(page, size), wrapper);

        List<ReportVO> voList = reportPage.getRecords().stream()
                .map(this::buildReportVO)
                .toList();

        Page<ReportVO> resultPage = new Page<>(reportPage.getCurrent(), reportPage.getSize(), reportPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    @Transactional
    public void handleReport(Long handlerId, Long reportId, HandleReportRequest request) {
        Report report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException("REPORT_NOT_FOUND", "举报记录不存在");
        }

        report.setStatus(request.status());
        report.setHandlerId(handlerId);
        report.setHandleNote(request.handleNote());
        report.setHandledAt(LocalDateTime.now());
        reportMapper.updateById(report);

        if (request.status() == 3) {
            hideTarget(report.getTargetType(), report.getTargetId());
        }
    }

    private void hideTarget(String targetType, Long targetId) {
        if ("POST".equals(targetType)) {
            Post post = postMapper.selectById(targetId);
            if (post != null) {
                post.setStatus(2);
                postMapper.updateById(post);
            }
        } else if ("COMMENT".equals(targetType)) {
            PostComment comment = postCommentMapper.selectById(targetId);
            if (comment != null) {
                comment.setStatus(1);
                postCommentMapper.updateById(comment);
            }
        }
    }

    private ReportVO buildReportVO(Report report) {
        return new ReportVO(
                report.getId(),
                report.getReporterId(),
                null,
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getDescription(),
                deserializeImages(report.getImages()),
                report.getStatus(),
                report.getHandlerId(),
                null,
                report.getHandleNote(),
                report.getHandledAt(),
                report.getCreatedAt()
        );
    }

    private String serializeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize report images: {}", e.getMessage());
            return null;
        }
    }

    private List<String> deserializeImages(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize report images: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
