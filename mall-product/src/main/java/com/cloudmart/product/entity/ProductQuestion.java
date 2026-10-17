package com.cloudmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品问答问题（PRODUCT-01 两表模型）：productId/author/status；
 * 答案在 product_answer（一问多答）。
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

    /** 0 正常 / 1 隐藏 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
