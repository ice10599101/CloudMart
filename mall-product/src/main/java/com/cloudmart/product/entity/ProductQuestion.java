package com.cloudmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品问答（N-5 问大家）：购前决策辅助。
 * 任意登录用户可提问；任意登录用户可回答（前端标注"已购"身份，购买快照落库）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("product_question")
public class ProductQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Long userId;

    private String question;

    private String answer;

    private Long answerUserId;

    private Boolean answerPurchased;

    /** 0 正常 / 1 隐藏 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime answeredAt;
}
