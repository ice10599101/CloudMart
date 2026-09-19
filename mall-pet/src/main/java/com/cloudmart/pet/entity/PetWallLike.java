package com.cloudmart.pet.entity;

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
 * 宠物留言点赞（{@code uk_pet_wall_like} 幂等：重复点赞不重复计数，取消即删行）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_wall_like")
public class PetWallLike {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 留言 ID */
    private Long messageId;

    /** 点赞用户 ID */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
