package com.cloudmart.community.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@TableName("post_tags")
public class PostTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long tagId;
}
