package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 心愿收藏（Sprint 1.5/3.6 补齐，文档 2.12）：uk(user,wish) 防重复。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_collection")
public class WishCollection {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long wishId;

    private LocalDateTime collectedAt;
}
