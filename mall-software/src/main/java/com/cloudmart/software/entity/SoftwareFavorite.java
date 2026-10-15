package com.cloudmart.software.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 收藏关系（方案 6.8）：唯一 (user_id, software_id)，幂等取消，物理删除 */
@Getter
@Setter
@TableName("software_favorite")
public class SoftwareFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long softwareId;

    private Long userId;

    private LocalDateTime createdAt;
}
