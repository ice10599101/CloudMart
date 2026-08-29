package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 品牌许愿池成员（uk 池×用户 防重复加入）。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_brand_pool_member")
public class BrandPoolMember {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long poolId;

    private Long userId;

    private LocalDateTime joinedAt;
}
