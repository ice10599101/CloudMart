package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 排行榜记录实体，持久化历史榜单数据。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("ranking_records")
public class RankingRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seasonId;

    private Long userId;

    /** 当月获得经验值 */
    private Integer expValue;

    /** 排名（从1开始） */
    private Integer rankNo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
