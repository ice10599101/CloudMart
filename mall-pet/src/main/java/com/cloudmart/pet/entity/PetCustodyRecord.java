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


@Getter
@Setter
@NoArgsConstructor
@TableName("pet_custody_record")
public class PetCustodyRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 有限托管（N05）：每用户每自然周 1 次，最长 24h；服务端定时照顾去重。 */
    private Long userId;
    private Long petId;
    private java.time.LocalDate weekStart;
    /** ACTIVE / ENDED */
    private String status;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime endsAt;
    /** 规则快照 JSON */
    private String ruleSnapshot;
    private Integer careFeedUsed;
    private Integer careCleanUsed;
    private java.time.LocalDateTime endedAt;;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
