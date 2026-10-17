package com.cloudmart.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品问答答案（PRODUCT-01 两表模型）：一问多答——第二人不能覆盖第一人；
 * 同人重复提交为编辑本人答案（uk(question, author) 兜底）。
 * 已购徽标为回答时快照（历史资格展示，不作为授权依据）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("product_answer")
public class ProductAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    /** 冗余商品 ID：归属校验（不同商品 questionId 不能混用）与列表查询 */
    private Long productId;

    private Long authorUserId;

    private String body;

    private Boolean purchased;

    /** 0 正常 / 1 隐藏 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
