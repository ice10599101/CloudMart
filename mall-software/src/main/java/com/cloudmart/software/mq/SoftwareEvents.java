package com.cloudmart.software.mq;

/**
 * 软件事件常量（方案 9.4）：独立 RocketMQ topic，不冒用既有社区 POST 通知事件。
 *
 * <p>业务 payload 至少含 softwareId/revisionId/publicationSeq/actorId，可选
 * targetUserId/commentId/rootCommentId/releaseKeys；禁止包含 URL、提取码、
 * 密码或整个正文。消费端按 eventId + recipientId 稳定去重。</p>
 */
public final class SoftwareEvents {

    /** 软件域专属事件 topic（独立消费者组与 ACL，复用现有 RocketMQ 实例） */
    public static final String TOPIC = "software-events";

    public static final String TAG_REVIEW_APPROVED = "SOFTWARE_REVIEW_APPROVED";
    public static final String TAG_REVIEW_REJECTED = "SOFTWARE_REVIEW_REJECTED";
    /** 举报处置：软件封禁（S10 通知分享者） */
    public static final String TAG_SOFTWARE_BANNED = "SOFTWARE_BANNED";
    public static final String TAG_COMMENT_VISIBLE = "SOFTWARE_COMMENT_VISIBLE";
    public static final String TAG_REPLY_VISIBLE = "SOFTWARE_REPLY_VISIBLE";
    public static final String TAG_SOURCE_REPORTED = "SOFTWARE_SOURCE_REPORTED";
    public static final String TAG_RELEASE_PUBLISHED = "SOFTWARE_RELEASE_PUBLISHED";
    /** SF03：发布者申请审核结果（通过/拒绝），载荷 {userId,status,reason} */
    public static final String TAG_PUBLISHER_APPLICATION = "SOFTWARE_PUBLISHER_APPLICATION";

    private SoftwareEvents() {
    }
}
