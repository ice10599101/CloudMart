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
 * 好友动态收件箱（F3）：事件发生时向 actor 的全部好友扇出写一行；
 * id 雪花趋势递增，兼作游标与已读水位依据。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_friend_feed")
public class PetFriendFeed {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 收件人用户 ID（好友） */
    private Long userId;

    /** 动态主体用户 ID */
    private Long actorUserId;

    /** 动态主体宠物 ID */
    private Long actorPetId;

    /** 事件类型：LEVEL_UP/WORK_COMPLETED/STUDY_COMPLETED/BATTLE_WIN */
    private String eventType;

    /** 展示载荷 JSON：{"text":"...","petName":"..."} */
    private String payloadJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
