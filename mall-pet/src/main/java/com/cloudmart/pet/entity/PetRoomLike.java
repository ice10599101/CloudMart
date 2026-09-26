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
 * 房间点赞持久化（B13）：同一用户对同一房间唯一点赞；取消置 active=0 但保留
 * rewarded 标记——重复取消再点不再发放经验。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_room_like")
public class PetRoomLike {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long roomId;

    /** 当前是否点赞（取消置 0） */
    private Boolean active;

    /** 是否已发过点赞经验（取消再点不再发奖） */
    private Boolean rewarded;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
