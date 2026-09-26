package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 内容举报（N01）：未结举报同内容同理由合并（dedup_key 结案置空释放唯一）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_report")
public class WishReport {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long reporterId;

    private String targetType;

    private Long targetId;

    /** SPAM / ABUSE / FRAUD / PRIVACY / OTHER */
    private String reasonCode;

    private String description;

    private String evidenceRefs;

    private Long caseId;

    /** PENDING / RESOLVED */
    private String status;

    private String dedupKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
