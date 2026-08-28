package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.cloudmart.wish.enums.MatchMemberRole;
import com.cloudmart.wish.enums.MatchMemberStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 小组成员（Sprint 2.6，文档 1.2 ⑧ wish_match_member）。
 *
 * <p>退出/被踢仅置 status=LEFT/KICKED 保留历史（文档验收：互动历史
 * 不删除）；同组同用户仅一条 ACTIVE 记录由功能唯一索引兜底。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_match_member")
public class MatchMember {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 小组 ID */
    private Long groupId;

    /** 用户 ID */
    private Long userId;

    /** 角色 */
    private MatchMemberRole role;

    /** 成员状态 */
    private MatchMemberStatus status;

    /** 入组留言（可空，≤200 字） */
    private String joinMessage;

    /** 加入时间（UTC） */
    private LocalDateTime joinedAt;

    /** 退出/被踢时间（UTC，业务层显式写入） */
    private LocalDateTime leftAt;
}
