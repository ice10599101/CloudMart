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
 * 星光转赠流水（§6/R06）：SETTLED 要求 amount = creditedAmount，
 * 双边流水（sender/receiver_ledger_id）等额异号；uk(from_user_id, request_key) 持久幂等。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("starlight_transfer")
public class StarlightTransfer {

    public static final String STATUS_SETTLED = "SETTLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    private Integer amount;

    /** 请求键（重放幂等；同键异载荷 409） */
    private String requestKey;

    private String status;

    /** 业务日（Asia/Shanghai，日额度归属） */
    private LocalDate businessDate;

    /** 接收方实际入账（守恒要求 = amount；不足整笔拒绝，禁止静默截断） */
    private Integer creditedAmount;

    private Long senderLedgerId;

    private Long receiverLedgerId;

    private Integer version;

    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
