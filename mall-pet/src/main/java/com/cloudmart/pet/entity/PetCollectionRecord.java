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
@TableName("pet_collection_record")
public class PetCollectionRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

/** 收藏记录（N07）：用户累计、多宠共享、仅解锁一次。 */
    private Long userId;
    private String entryCode;
    private Long firstPetId;
    private String firstEventId;
    private java.time.LocalDateTime acquiredAt;;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
