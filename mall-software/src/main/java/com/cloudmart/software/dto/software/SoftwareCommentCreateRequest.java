package com.cloudmart.software.dto.software;

import lombok.Getter;
import lombok.Setter;

/**
 * 评论创建请求（方案 7.3/4.4）：只传 parentId（根楼，可空）与 replyToCommentId
 * （精确回复目标，可空）；replyToUserId 由服务端从目标评论推导。
 */
@Getter
@Setter
public class SoftwareCommentCreateRequest {

    /** 评论正文（纯文本+表情，1~1000 字） */
    private String content;

    /** 根评论 ID（楼中楼时必填，恒指向根楼） */
    private Long parentId;

    /** 精确回复目标评论 ID（同软件同根楼内） */
    private Long replyToCommentId;
}
