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
 * 用户级写锁（B01/§3.3）：并发写路径（长期互斥/日额度组合/陪伴会话等）先
 * SELECT FOR UPDATE 本行串行化同一用户的写操作；跨用户社交按 userId 升序锁两个 guard。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("pet_user_guard")
public class PetUserGuard {

    /** 用户 ID（主键即锁粒度） */
    @TableId(type = IdType.INPUT)
    private Long userId;

    private Long version;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
