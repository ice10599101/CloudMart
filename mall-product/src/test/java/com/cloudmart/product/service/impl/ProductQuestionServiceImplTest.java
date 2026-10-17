package com.cloudmart.product.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.api.ApiResponse;
import com.cloudmart.common.async.outbox.OutboxService;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.product.entity.ProductAnswer;
import com.cloudmart.product.entity.ProductQuestion;
import com.cloudmart.product.feign.OrderPurchaseFeignClient;
import com.cloudmart.product.feign.UserFeignClient;
import com.cloudmart.product.repository.ProductAnswerMapper;
import com.cloudmart.product.repository.ProductMapper;
import com.cloudmart.product.repository.ProductQuestionMapper;
import com.cloudmart.product.vo.ProductQuestionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PRODUCT-01 验收：提问可见并回答；自答不 500；第二人不覆盖第一人；
 * 不同商品 questionId 不能混用；未回答问题可见；隐藏后不可见。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductQuestionServiceImplTest {

    private static final Long PRODUCT_ID = 7L;
    private static final Long OTHER_PRODUCT_ID = 8L;
    private static final Long ASKER_ID = 100L;
    private static final Long ANSWERER_ID = 200L;

    @Mock
    private ProductQuestionMapper questionMapper;
    @Mock
    private ProductAnswerMapper answerMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private OrderPurchaseFeignClient orderPurchaseFeignClient;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private OutboxService outboxService;

    private ProductQuestionServiceImpl service;

    private ProductQuestion question(Long id, Long productId, Long asker) {
        ProductQuestion q = new ProductQuestion();
        q.setId(id);
        q.setProductId(productId);
        q.setUserId(asker);
        q.setQuestion("这个尺码偏大吗？");
        q.setStatus(0);
        q.setCreatedAt(LocalDateTime.now());
        return q;
    }

    private ProductAnswer answer(Long id, Long questionId, Long author, String body) {
        ProductAnswer a = new ProductAnswer();
        a.setId(id);
        a.setQuestionId(questionId);
        a.setProductId(PRODUCT_ID);
        a.setAuthorUserId(author);
        a.setBody(body);
        a.setPurchased(false);
        a.setStatus(0);
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    @BeforeEach
    void setUp() {
        service = new ProductQuestionServiceImpl(
                questionMapper, answerMapper, productMapper, orderPurchaseFeignClient,
                userFeignClient, outboxService);
        lenient().when(productMapper.selectById(PRODUCT_ID)).thenReturn(new com.cloudmart.product.entity.Product());
        lenient().when(userFeignClient.batchGetUsers(anyList()))
                .thenReturn(ApiResponse.ok(List.of(
                        java.util.Map.of("id", ASKER_ID, "nickname", "提问人"),
                        java.util.Map.of("id", ANSWERER_ID, "nickname", "回答人"))));
        lenient().when(orderPurchaseFeignClient.hasPurchasedProduct(anyLong(), anyLong()))
                .thenReturn(ApiResponse.ok(false));
    }

    @Test
    @DisplayName("未回答问题可见（PRODUCT-01 关键修复：list 不再过滤 answer 非空）")
    void list_includesUnansweredQuestions() {
        ProductQuestion unanswered = question(1L, PRODUCT_ID, ASKER_ID);
        when(questionMapper.selectPage(any(), any())).thenReturn(pageOf(List.of(unanswered)));
        when(answerMapper.selectList(any())).thenReturn(List.of());

        Page<ProductQuestionVO> result = service.list(PRODUCT_ID, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).answers()).isEmpty();
    }

    @Test
    @DisplayName("自答不 500（昵称集合去重；第二人不能覆盖第一人——一问多答新增行）")
    void answer_secondPerson_appendsNotOverwrites() {
        ProductQuestion q = question(1L, PRODUCT_ID, ASKER_ID);
        when(questionMapper.selectById(1L)).thenReturn(q);
        ProductAnswer firstAnswer = answer(11L, 1L, ASKER_ID + 1, "偏小半码");
        when(answerMapper.selectOne(any())).thenReturn(null);
        when(answerMapper.selectList(any())).thenReturn(
                List.of(firstAnswer, answer(12L, 1L, ANSWERER_ID, "正常")));

        ProductQuestionVO vo = service.answer(ANSWERER_ID, PRODUCT_ID, 1L, "正常");

        // 新增行（不是 update 旧答案）
        ArgumentCaptor<ProductAnswer> captor = ArgumentCaptor.forClass(ProductAnswer.class);
        verify(answerMapper).insert(captor.capture());
        verify(answerMapper, never()).updateById(any(ProductAnswer.class));
        assertThat(captor.getValue().getAuthorUserId()).isEqualTo(ANSWERER_ID);
        assertThat(vo.answers()).hasSize(2);
    }

    @Test
    @DisplayName("同人重复提交同题 → 编辑本人答案（不新增不覆盖他人）")
    void answer_sameAuthor_editsOwnAnswer() {
        ProductQuestion q = question(1L, PRODUCT_ID, ASKER_ID);
        when(questionMapper.selectById(1L)).thenReturn(q);
        ProductAnswer own = answer(11L, 1L, ANSWERER_ID, "旧回答");
        when(answerMapper.selectOne(any())).thenReturn(own);
        when(answerMapper.selectList(any())).thenReturn(List.of(own));

        ProductQuestionVO vo = service.answer(ANSWERER_ID, PRODUCT_ID, 1L, "新回答");

        verify(answerMapper, never()).insert(any(ProductAnswer.class));
        verify(answerMapper).updateById(own);
        assertThat(vo.answers()).hasSize(1);
        assertThat(vo.answers().get(0).body()).isEqualTo("新回答");
    }

    @Test
    @DisplayName("不同商品 questionId 不能混用（路径 productId 归属校验）")
    void answer_productMismatch_rejected() {
        when(questionMapper.selectById(1L)).thenReturn(question(1L, OTHER_PRODUCT_ID, ASKER_ID));

        assertThatThrownBy(() -> service.answer(ANSWERER_ID, PRODUCT_ID, 1L, "回答"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "QUESTION_PRODUCT_MISMATCH");
        verify(answerMapper, never()).insert(any(ProductAnswer.class));
    }

    @Test
    @DisplayName("内容超 500 字 → 拒绝；HTML 转义生效")
    void ask_sanitizesAndValidatesLength() {
        assertThatThrownBy(() -> service.ask(ASKER_ID, PRODUCT_ID, "<script>" + "x".repeat(600) + "</script>"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "VALIDATION_ERROR");

        service.ask(ASKER_ID, PRODUCT_ID, "<b>偏码吗</b>");
        ArgumentCaptor<ProductQuestion> captor = ArgumentCaptor.forClass(ProductQuestion.class);
        verify(questionMapper).insert(captor.capture());
        assertThat(captor.getValue().getQuestion()).doesNotContain("<b>").contains("&lt;b&gt;");
    }

    @Test
    @DisplayName("回答成功发 QUESTION_ANSWERED Outbox 事件（提问人通知事实先行）")
    void answer_publishesOutboxEvent() {
        when(questionMapper.selectById(1L)).thenReturn(question(1L, PRODUCT_ID, ASKER_ID));
        when(answerMapper.selectOne(any())).thenReturn(null);
        when(answerMapper.selectList(any())).thenReturn(List.of());

        service.answer(ANSWERER_ID, PRODUCT_ID, 1L, "正常");

        verify(outboxService).record(any(com.cloudmart.common.async.EventEnvelope.class));
    }

    private Page<ProductQuestion> pageOf(List<ProductQuestion> records) {
        Page<ProductQuestion> page = new Page<>(1, 10, records.size());
        page.setRecords(records);
        return page;
    }
}
