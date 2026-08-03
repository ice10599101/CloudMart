package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.community.entity.SensitiveWord;
import com.cloudmart.community.repository.SensitiveWordMapper;
import com.cloudmart.community.service.ContentReviewService.ReviewResult;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentReviewServiceImplTest {

    @Mock
    private SensitiveWordMapper sensitiveWordMapper;

    private ContentReviewServiceImpl contentReviewService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.cloudmart.community.repository.SensitiveWordMapper");
        TableInfoHelper.initTableInfo(assistant, SensitiveWord.class);
    }

    @BeforeEach
    void setUp() {
        contentReviewService = new ContentReviewServiceImpl(sensitiveWordMapper);
    }

    private SensitiveWord buildSensitiveWord(Long id, String word, String category, int level) {
        SensitiveWord sw = new SensitiveWord();
        sw.setId(id);
        sw.setWord(word);
        sw.setCategory(category);
        sw.setLevel(level);
        return sw;
    }

    private void mockCacheLoad(List<SensitiveWord> words) {
        when(sensitiveWordMapper.selectList(any())).thenReturn(words);
    }

    @Nested
    @DisplayName("reviewContent")
    class ReviewContentTests {

        @Test
        @DisplayName("should approve null content")
        void reviewContent_nullContent_approved() {
            ReviewResult result = contentReviewService.reviewContent(null);

            assertThat(result.approved()).isTrue();
            assertThat(result.needsManualReview()).isFalse();
            assertThat(result.filteredContent()).isNull();
        }

        @Test
        @DisplayName("should approve blank content")
        void reviewContent_blankContent_approved() {
            ReviewResult result = contentReviewService.reviewContent("   ");

            assertThat(result.approved()).isTrue();
            assertThat(result.needsManualReview()).isFalse();
        }

        @Test
        @DisplayName("should reject content containing level-3 word")
        void reviewContent_level3Word_rejected() {
            mockCacheLoad(List.of(buildSensitiveWord(1L, "违禁词", "POLITICS", 3)));

            ReviewResult result = contentReviewService.reviewContent("这是一段包含违禁词的内容");

            assertThat(result.approved()).isFalse();
            assertThat(result.needsManualReview()).isFalse();
            assertThat(result.reason()).isEqualTo("内容包含违规词汇，发布被拒绝");
        }

        @Test
        @DisplayName("should flag content for manual review when level-2 word found")
        void reviewContent_level2Word_needsManualReview() {
            mockCacheLoad(List.of(buildSensitiveWord(1L, "敏感词", "GENERAL", 2)));

            ReviewResult result = contentReviewService.reviewContent("这是一段包含敏感词的内容");

            assertThat(result.approved()).isTrue();
            assertThat(result.needsManualReview()).isTrue();
            assertThat(result.reason()).isEqualTo("内容需人工审核");
        }

        @Test
        @DisplayName("should replace level-1 word with asterisks")
        void reviewContent_level1Word_filtered() {
            mockCacheLoad(List.of(buildSensitiveWord(1L, "脏话", "GENERAL", 1)));

            ReviewResult result = contentReviewService.reviewContent("这是一段包含脏话的内容");

            assertThat(result.approved()).isTrue();
            assertThat(result.needsManualReview()).isFalse();
            assertThat(result.filteredContent()).isEqualTo("这是一段包含**的内容");
        }

        @Test
        @DisplayName("should approve clean content")
        void reviewContent_cleanContent_approved() {
            mockCacheLoad(List.of(buildSensitiveWord(1L, "违禁词", "POLITICS", 3)));

            ReviewResult result = contentReviewService.reviewContent("这是一段正常内容");

            assertThat(result.approved()).isTrue();
            assertThat(result.needsManualReview()).isFalse();
            assertThat(result.filteredContent()).isEqualTo("这是一段正常内容");
        }
    }

    @Nested
    @DisplayName("listSensitiveWords")
    class ListSensitiveWordsTests {

        @Test
        @DisplayName("should return paginated list")
        void listSensitiveWords_success() {
            SensitiveWord sw = buildSensitiveWord(1L, "test", "GENERAL", 1);
            Page<SensitiveWord> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(sw));
            when(sensitiveWordMapper.selectPage(any(Page.class), any())).thenReturn(page);

            List<SensitiveWord> result = contentReviewService.listSensitiveWords(null, 1, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getWord()).isEqualTo("test");
        }

        @Test
        @DisplayName("should filter by category when provided")
        void listSensitiveWords_withCategory() {
            Page<SensitiveWord> page = new Page<>(1, 10, 0);
            page.setRecords(List.of());
            when(sensitiveWordMapper.selectPage(any(Page.class), any())).thenReturn(page);

            List<SensitiveWord> result = contentReviewService.listSensitiveWords("POLITICS", 1, 10);

            assertThat(result).isEmpty();
            verify(sensitiveWordMapper).selectPage(any(Page.class), any());
        }
    }

    @Nested
    @DisplayName("addSensitiveWord")
    class AddSensitiveWordTests {

        @Test
        @DisplayName("should add new sensitive word and update cache")
        void addSensitiveWord_success() {
            when(sensitiveWordMapper.selectOne(any())).thenReturn(null);
            when(sensitiveWordMapper.insert(any(SensitiveWord.class))).thenAnswer(invocation -> {
                SensitiveWord sw = invocation.getArgument(0);
                sw.setId(1L);
                return 1;
            });

            SensitiveWord result = contentReviewService.addSensitiveWord("新词", "GENERAL", 1);

            assertThat(result).isNotNull();
            assertThat(result.getWord()).isEqualTo("新词");
            assertThat(result.getCategory()).isEqualTo("GENERAL");
            assertThat(result.getLevel()).isEqualTo(1);
            verify(sensitiveWordMapper).insert(any(SensitiveWord.class));
        }

        @Test
        @DisplayName("should default category to GENERAL when null")
        void addSensitiveWord_nullCategory_defaultsToGeneral() {
            when(sensitiveWordMapper.selectOne(any())).thenReturn(null);
            when(sensitiveWordMapper.insert(any(SensitiveWord.class))).thenAnswer(invocation -> {
                SensitiveWord sw = invocation.getArgument(0);
                sw.setId(1L);
                return 1;
            });

            SensitiveWord result = contentReviewService.addSensitiveWord("新词", null, 2);

            assertThat(result.getCategory()).isEqualTo("GENERAL");
        }

        @Test
        @DisplayName("should throw when word is blank")
        void addSensitiveWord_blankWord_throwsException() {
            assertThatThrownBy(() -> contentReviewService.addSensitiveWord("  ", "GENERAL", 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("INVALID_WORD");
                    });

            verify(sensitiveWordMapper, never()).insert(any(SensitiveWord.class));
        }

        @Test
        @DisplayName("should throw when word already exists")
        void addSensitiveWord_wordExists_throwsException() {
            when(sensitiveWordMapper.selectOne(any())).thenReturn(buildSensitiveWord(1L, "已存在", "GENERAL", 1));

            assertThatThrownBy(() -> contentReviewService.addSensitiveWord("已存在", "GENERAL", 1))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("WORD_EXISTS");
                    });

            verify(sensitiveWordMapper, never()).insert(any(SensitiveWord.class));
        }
    }

    @Nested
    @DisplayName("removeSensitiveWord")
    class RemoveSensitiveWordTests {

        @Test
        @DisplayName("should remove existing word and clear cache")
        void removeSensitiveWord_success() {
            SensitiveWord sw = buildSensitiveWord(1L, "删除词", "GENERAL", 1);
            when(sensitiveWordMapper.selectById(1L)).thenReturn(sw);

            contentReviewService.removeSensitiveWord(1L);

            verify(sensitiveWordMapper).deleteById(anyLong());
        }

        @Test
        @DisplayName("should do nothing when word not found")
        void removeSensitiveWord_notFound_noAction() {
            when(sensitiveWordMapper.selectById(999L)).thenReturn(null);

            contentReviewService.removeSensitiveWord(999L);

            verify(sensitiveWordMapper, never()).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("refreshCache")
    class RefreshCacheTests {

        @Test
        @DisplayName("should reload cache from database")
        void refreshCache_success() {
            List<SensitiveWord> words = List.of(
                    buildSensitiveWord(1L, "词1", "GENERAL", 1),
                    buildSensitiveWord(2L, "词2", "POLITICS", 3)
            );
            when(sensitiveWordMapper.selectList(any())).thenReturn(words);

            contentReviewService.refreshCache();

            verify(sensitiveWordMapper).selectList(any());
        }
    }
}
