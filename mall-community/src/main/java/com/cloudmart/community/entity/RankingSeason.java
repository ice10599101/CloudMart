package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排行榜赛季实体，每月一个赛季。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("ranking_seasons")
public class RankingSeason {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 赛季标识，格式 yyyyMM，如 202607 */
    private String seasonKey;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 状态：0-进行中，1-已归档 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
