package com.cloudmart.wish.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 礼物赠送记录实体（V37 迁移，全站虚拟礼物）。
 *
 * <p>每次成功送礼插入一条，为星光消费凭证（历史事实）：取消/退款不删除，
 * {@code giftName/giftIconUrl/unitPrice} 为送礼时点快照，目录后续变更不回溯。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("wish_gift_record")
public class GiftRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 送礼人用户 ID */
    private Long senderId;

    /** 收礼人用户 ID */
    private Long receiverId;

    /** 礼物 ID */
    private Long giftId;

    /** 礼物名称快照 */
    private String giftName;

    /** 礼物图标快照 */
    private String giftIconUrl;

    /** 送礼时单价快照（星光） */
    private Integer unitPrice;

    /** 数量（1-99） */
    private Integer count;

    /** 总消耗（星光）= unitPrice * count */
    private Integer totalPrice;

    /** 送礼场景：WISH / POST / LIVE_ROOM */
    private String targetType;

    /** 场景对象 ID（心愿 ID / 帖子 ID / 直播间 ID） */
    private Long targetId;

    /** 送礼留言（可选） */
    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private LocalDateTime deletedAt;
}
