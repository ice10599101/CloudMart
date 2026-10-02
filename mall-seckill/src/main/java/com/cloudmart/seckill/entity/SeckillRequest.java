package com.cloudmart.seckill.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀请求事实（T09）：一行即一次购买资格占用。
 *
 * <p>{@code requestId} 在提交时生成，贯穿 MQ 消息、订单 request_key 与结果查询；
 * {@code (activity_id, product_id, user_id)} 唯一键是购买限额的权威事实——
 * Redis 预筛丢失/重建后以本表为准；{@code seckillPrice} 为提交时冻结的成交价
 * 快照，订单服务经内部接口引用本快照计价，禁止客户端价格参与。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("seckill_request")
public class SeckillRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private Long activityId;

    private Long productId;

    private Long skuId;

    private Long userId;

    private Integer quantity;

    private BigDecimal seckillPrice;

    private String status;

    private Long orderId;

    private String failReason;

    private Integer sendAttempts;

    private LocalDateTime nextRetryAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
