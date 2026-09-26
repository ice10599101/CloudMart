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
 * 陪伴会话（B05）：服务端计时权威，前端上报秒数仅作参考。
 *
 * <p>每用户至多一条 ACTIVE 会话（函数唯一索引兜底，多端只累计一份有效时间）；
 * 心跳以服务端时钟差计有效时长，超过失效间隔未心跳的会话结束且不补计中断区间；
 * last_seq 单调递增去重重放心跳。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_companion_session")
public class PetCompanionSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long petId;

    /** ACTIVE/ENDED */
    private String status;

    /** 服务端会话基准（首次心跳时间，不凭空增加时长） */
    private LocalDateTime startedAt;

    /** 最近有效心跳时间（计时游标） */
    private LocalDateTime lastHeartbeatAt;

    private Long lastSeq;

    private LocalDateTime endedAt;

    /** STOPPED/EXPIRED/SUPERSEDED */
    private String endReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
