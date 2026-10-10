package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 星光转赠流水（§6）：spend/earn 对转共享 refId；审计与日限额口径。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("starlight_transfer")
public class StarlightTransfer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    private Integer amount;

    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
