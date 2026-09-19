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
 * 宠物好友（申请单向行，确认时双向各落一行 ACTIVE）。
 *
 * <p>{@code uk_pet_friend} 幂等：重复申请/重复确认不会产生第二行；
 * 互访会刷新 {@code lastVisitAt/visitCount}，并给双方宠物发经验（服务端结算）。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_friend")
public class PetFriend {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 好友用户 ID */
    private Long friendUserId;

    /** 状态：PENDING/ACTIVE/REJECTED */
    private String status;

    /** 来源：VISIT/SEARCH/RELATION */
    private String source;

    /** 我访问好友次数 */
    private Integer visitCount;

    /** 最近一次互访时间（UTC） */
    private LocalDateTime lastVisitAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
