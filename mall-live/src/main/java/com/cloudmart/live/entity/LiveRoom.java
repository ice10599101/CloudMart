package com.cloudmart.live.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@TableName("live_rooms")
public class LiveRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;
    private String description;
    private Long anchorUserId;
    private String anchorName;
    private String coverImage;
    private String streamUrl;
    private Long productId;
    private Long seckillActivityId;
    private Integer maxViewers;
    private Integer currentViewers;
    private Long totalViewers;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
