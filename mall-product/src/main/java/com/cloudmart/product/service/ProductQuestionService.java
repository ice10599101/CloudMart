package com.cloudmart.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.product.vo.ProductQuestionVO;

/**
 * 商品问答（N-5 问大家）：提问/分页/回答。
 */
public interface ProductQuestionService {

    ProductQuestionVO ask(Long userId, Long productId, String question);

    Page<ProductQuestionVO> list(Long productId, int page, int size);

    ProductQuestionVO answer(Long userId, Long questionId, String answer);
}
