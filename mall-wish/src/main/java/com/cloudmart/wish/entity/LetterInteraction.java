package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 信笺匿名互动（Sprint 3.3，文档 1.2 ㉚）：BLESS 免费 / LIGHT 扣星光 2
 * 并点亮对方心愿；uk(letter,user,互动日) = 单信笺每用户每日 1 次。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_encounter_letter_interaction")
public class LetterInteraction {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 信笺 ID */
    private Long letterId;

    /** 互动发起者（信笺归属人） */
    private Long userId;

    /** 互动类型：BLESS 匿名祝福 / LIGHT 点亮对方心愿 */
    private String type;

    /** 对方心愿 ID（LIGHT 时 support_count +1） */
    private Long peerWishId;

    /** 互动日期（用户时区日，幂等键） */
    private LocalDate interactDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
