package com.cloudmart.product.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.product.vo.ProductQuestionVO;

/**
 * 商品问答（PRODUCT-01 两表模型）：提问/分页（未答可见）/回答/我的问答。
 */
public interface ProductQuestionService {

    ProductQuestionVO ask(Long userId, Long productId, String question);

    Page<ProductQuestionVO> list(Long productId, int page, int size);

    /** 回答：归属校验（路径 productId 必须与问题一致）；同人重复提交为编辑本人答案 */
    ProductQuestionVO answer(Long userId, Long productId, Long questionId, String answer);

    /** 我的问答（本人提问，含回答进度） */
    Page<ProductQuestionVO> myQuestions(Long userId, int page, int size);

    /**
     * T13：删除自己的提问（用户级；仅本人，服务端校验归属）。
     * 软删 status=2（用户删除）——商品详情/我的提问不再可见（含已隐藏 1 同样不可恢复展示）；
     * 已有回答一并随问题不可见。重复删除幂等。
     */
    void deleteOwn(Long userId, Long questionId);

    // ===== PRODUCT-01 管理端（商城→商品问答；隐藏/恢复单独权限与审计在网关代理层） =====

    /** 管理分页：可按商品/状态筛选（含隐藏项与全部答案，供审核处置） */
    Page<ProductQuestionVO> adminPage(Long productId, Integer status, int page, int size);

    /** 隐藏问题（运营处置；隐藏后所有端不可见） */
    void adminHideQuestion(Long questionId, String reason);

    /** 恢复问题 */
    void adminRestoreQuestion(Long questionId);

    /** 隐藏答案 */
    void adminHideAnswer(Long answerId, String reason);

    /** 恢复答案 */
    void adminRestoreAnswer(Long answerId);
}
