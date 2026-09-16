package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 浏览足迹：用户浏览商品/帖子/心愿等对象的记录。
 * uk(user_id, target_type, target_id) 保证同一对象只有一条记录，每次浏览仅刷新 viewed_at 与标题/封面快照。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("browse_histories")
public class BrowseHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String targetType;

    private Long targetId;

    private String title;

    private String cover;

    private LocalDateTime viewedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}