package com.cloudmart.software.vo.software;

/** 失效反馈结果（方案 5.3）：重复提交返回现有反馈（同指纹同人一条开放反馈） */
public record SoftwareFeedbackVO(
        Long feedbackId,
        String sourceKey,
        String reasonCode,
        String status) {
}
