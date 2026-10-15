package com.cloudmart.software.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 小答软件模块业务配置（方案 11.2：software.* 前缀）。
 *
 * <p>初始产品默认值：功能开关默认关闭（上线前 false，按灰度逐步打开）；
 * 发布资格默认拒绝（publisher-default-enabled 固定 false，缺失权限记录也拒绝，
 * 不允许以全站开关批量开通投稿）。限流阈值是初始产品默认值，部署压测后可经
 * Nacos 热更新调整。</p>
 */
@Component
@ConfigurationProperties(prefix = "software")
public class SoftwareProperties {

    /** 模块总开关：关闭后公共软件读写返回功能不可用；后台仍可审核处置 */
    private boolean enabled = false;

    /** 投稿开关：控制提交审核 / 重新上架（作者保存草稿另受发布资格约束） */
    private boolean publishEnabled = false;

    /** 获取链接开关：控制 resolve；关闭后旧前端也无法获取（服务端生效，不依赖前端隐藏按钮） */
    private boolean resolveEnabled = false;

    /** resolve 是否强制登录：三种查看模式的共同前提，不得关闭以绕过等级/付款授权 */
    private final boolean resolveLoginRequired = true;

    /** 全站默认发布资格：本期固定 false，仅后台逐用户开通（4.0 发布资格矩阵） */
    private final boolean publisherDefaultEnabled = false;

    /** 单用户每日提交审核上限 */
    private int publishSubmissionsPerUserDay = 10;

    /** 单用户每分钟评论上限 */
    private int commentPerUserMinute = 5;

    /** 单用户每分钟获取链接上限 */
    private int resolvePerUserMinute = 30;

    /** 单用户每日失效反馈上限 */
    private int feedbackPerUserDay = 20;

    /** 单用户每日举报上限 */
    private int reportPerUserDay = 20;

    /** 单个快照最大版本数 */
    private int revisionsMaxReleases = 50;

    /** 单个版本最大来源数 */
    private int releaseMaxSources = 10;

    /** 单快照截图上限 */
    private int screenshotMaxCount = 8;

    /** 来源失效待核实阈值：24 小时内同一指纹收到 N 个不同账号反馈即标 SUSPECTED */
    private int suspectedDistinctReporters = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPublishEnabled() {
        return publishEnabled;
    }

    public void setPublishEnabled(boolean publishEnabled) {
        this.publishEnabled = publishEnabled;
    }

    public boolean isResolveEnabled() {
        return resolveEnabled;
    }

    public void setResolveEnabled(boolean resolveEnabled) {
        this.resolveEnabled = resolveEnabled;
    }

    public boolean isResolveLoginRequired() {
        return resolveLoginRequired;
    }

    public boolean isPublisherDefaultEnabled() {
        return publisherDefaultEnabled;
    }

    public int getPublishSubmissionsPerUserDay() {
        return publishSubmissionsPerUserDay;
    }

    public void setPublishSubmissionsPerUserDay(int publishSubmissionsPerUserDay) {
        this.publishSubmissionsPerUserDay = publishSubmissionsPerUserDay;
    }

    public int getCommentPerUserMinute() {
        return commentPerUserMinute;
    }

    public void setCommentPerUserMinute(int commentPerUserMinute) {
        this.commentPerUserMinute = commentPerUserMinute;
    }

    public int getResolvePerUserMinute() {
        return resolvePerUserMinute;
    }

    public void setResolvePerUserMinute(int resolvePerUserMinute) {
        this.resolvePerUserMinute = resolvePerUserMinute;
    }

    public int getFeedbackPerUserDay() {
        return feedbackPerUserDay;
    }

    public void setFeedbackPerUserDay(int feedbackPerUserDay) {
        this.feedbackPerUserDay = feedbackPerUserDay;
    }

    public int getReportPerUserDay() {
        return reportPerUserDay;
    }

    public void setReportPerUserDay(int reportPerUserDay) {
        this.reportPerUserDay = reportPerUserDay;
    }

    public int getRevisionsMaxReleases() {
        return revisionsMaxReleases;
    }

    public void setRevisionsMaxReleases(int revisionsMaxReleases) {
        this.revisionsMaxReleases = revisionsMaxReleases;
    }

    public int getReleaseMaxSources() {
        return releaseMaxSources;
    }

    public void setReleaseMaxSources(int releaseMaxSources) {
        this.releaseMaxSources = releaseMaxSources;
    }

    public int getScreenshotMaxCount() {
        return screenshotMaxCount;
    }

    public void setScreenshotMaxCount(int screenshotMaxCount) {
        this.screenshotMaxCount = screenshotMaxCount;
    }

    public int getSuspectedDistinctReporters() {
        return suspectedDistinctReporters;
    }

    public void setSuspectedDistinctReporters(int suspectedDistinctReporters) {
        this.suspectedDistinctReporters = suspectedDistinctReporters;
    }
}
