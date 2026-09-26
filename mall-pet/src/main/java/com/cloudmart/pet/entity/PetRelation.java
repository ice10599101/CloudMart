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
 * 宠物关系（情侣/闺蜜/兄弟/死党）。
 *
 * <p>语义：{@code fromPetId} 发起 → {@code toPetId} 主人确认后置 ACTIVE。
 * {@code uk_pet_relation} 保证同一对宠物同一类型只有一条，重复申请由 DB 兜底；
 * 关系亲密度由互访好友房间、双方留言、彼此对战等行为累积。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_relation")
public class PetRelation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 发起方宠物 ID */
    private Long fromPetId;

    /** 规范化较小宠物 ID（B14 无向去重） */
    private Long petAId;

    /** 规范化较大宠物 ID（B14 无向去重） */
    private Long petBId;

    /** 接收方宠物 ID */
    private Long toPetId;

    /** 发起方主人用户 ID */
    private Long fromUserId;

    /** 接收方主人用户 ID */
    private Long toUserId;

    /** 关系类型：COUPLE/BESTIE/BROTHER/CONFIDANT */
    private String relType;

    /** 状态：PENDING/ACTIVE/REJECTED/DISSOLVED */
    private String status;

    /** 关系亲密度（互访/留言/对战累积） */
    private Integer intimacy;

    /** 申请留言（展示用） */
    private String message;

    /** 确认时间（UTC） */
    private LocalDateTime acceptedAt;

    /** 最近一次亲密度增长时间（UTC） */
    private LocalDateTime lastIntimacyAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
