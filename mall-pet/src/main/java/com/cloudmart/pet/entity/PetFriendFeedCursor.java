package com.cloudmart.pet.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 好友动态已读水位（F3）：user_id 主键，last_read_id 之后的为未读。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_friend_feed_cursor")
public class PetFriendFeedCursor {

    @TableId
    private Long userId;

    private Long lastReadId;

    /** 数据库 ON UPDATE 维护，业务代码禁止写入 */
    private LocalDateTime updatedAt;
}
