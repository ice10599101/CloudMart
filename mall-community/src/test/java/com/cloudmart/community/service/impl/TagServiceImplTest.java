package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateTagRequest;
import com.cloudmart.community.dto.UpdateTagRequest;
import com.cloudmart.community.entity.Tag;
import com.cloudmart.community.repository.TagMapper;
import com.cloudmart.community.service.CommunityCacheService;
import com.cloudmart.community.vo.TagVO;
import com.cloudmart.common.exception.BusinessException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private CommunityCacheService communityCacheService;

    private TagServiceImpl tagService;

    private static final Long TAG_ID = 1L;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("com.cloudmart.community.repository.TagMapper");
        TableInfoHelper.initTableInfo(assistant, Tag.class);
    }

    @BeforeEach
    void setUp() {
        tagService = new TagServiceImpl(tagMapper, communityCacheService);
    }

    private Tag buildTag() {
        Tag tag = new Tag();
        tag.setId(TAG_ID);
        tag.setName("Java");
        tag.setIcon("java-icon");
        tag.setPostCount(10);
        tag.setIsHot(true);
        tag.setStatus(1);
        return tag;
    }

    @Nested
    @DisplayName("createTag")
    class CreateTagTests {

        @Test
        @DisplayName("should create tag and return TagVO")
        void createTag_success() {
            CreateTagRequest request = new CreateTagRequest("Java", "java-icon");
            when(tagMapper.selectCount(any())).thenReturn(0L);
            when(tagMapper.insert(any(Tag.class))).thenAnswer(invocation -> {
                Tag tag = invocation.getArgument(0);
                tag.setId(TAG_ID);
                return 1;
            });

            TagVO result = tagService.createTag(request);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Java");
            assertThat(result.icon()).isEqualTo("java-icon");
            assertThat(result.postCount()).isEqualTo(0);
            assertThat(result.isHot()).isFalse();
            assertThat(result.status()).isEqualTo(1);
            verify(tagMapper).insert(any(Tag.class));
        }

        @Test
        @DisplayName("should throw when tag name already exists")
        void createTag_duplicateName_throwsException() {
            CreateTagRequest request = new CreateTagRequest("Java", "java-icon");
            when(tagMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> tagService.createTag(request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("TAG_NAME_DUPLICATE");
                    });

            verify(tagMapper, never()).insert(any(Tag.class));
        }
    }

    @Nested
    @DisplayName("updateTag")
    class UpdateTagTests {

        @Test
        @DisplayName("should update tag fields and return TagVO")
        void updateTag_success() {
            Tag tag = buildTag();
            when(tagMapper.selectById(TAG_ID)).thenReturn(tag);

            UpdateTagRequest request = new UpdateTagRequest("Spring", "spring-icon", 0);
            TagVO result = tagService.updateTag(TAG_ID, request);

            assertThat(result).isNotNull();
            assertThat(tag.getName()).isEqualTo("Spring");
            assertThat(tag.getIcon()).isEqualTo("spring-icon");
            assertThat(tag.getStatus()).isEqualTo(0);
            verify(tagMapper).updateById(tag);
        }

        @Test
        @DisplayName("should throw when tag not found")
        void updateTag_notFound_throwsException() {
            when(tagMapper.selectById(TAG_ID)).thenReturn(null);

            UpdateTagRequest request = new UpdateTagRequest("Spring", null, null);

            assertThatThrownBy(() -> tagService.updateTag(TAG_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("TAG_NOT_FOUND");
                    });

            verify(tagMapper, never()).updateById(any(Tag.class));
        }

        @Test
        @DisplayName("should only update non-null fields")
        void updateTag_partialUpdate() {
            Tag tag = buildTag();
            when(tagMapper.selectById(TAG_ID)).thenReturn(tag);

            UpdateTagRequest request = new UpdateTagRequest(null, "new-icon", null);
            tagService.updateTag(TAG_ID, request);

            assertThat(tag.getName()).isEqualTo("Java");
            assertThat(tag.getIcon()).isEqualTo("new-icon");
            assertThat(tag.getStatus()).isEqualTo(1);
            verify(tagMapper).updateById(tag);
        }
    }

    @Nested
    @DisplayName("deleteTag")
    class DeleteTagTests {

        @Test
        @DisplayName("should delete tag by id")
        void deleteTag_success() {
            tagService.deleteTag(TAG_ID);

            verify(tagMapper).deleteById(anyLong());
        }
    }

    @Nested
    @DisplayName("getTagById")
    class GetTagByIdTests {

        @Test
        @DisplayName("should return TagVO when tag exists")
        void getTagById_success() {
            Tag tag = buildTag();
            when(tagMapper.selectById(TAG_ID)).thenReturn(tag);

            TagVO result = tagService.getTagById(TAG_ID);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Java");
        }

        @Test
        @DisplayName("should throw when tag not found")
        void getTagById_notFound_throwsException() {
            when(tagMapper.selectById(TAG_ID)).thenReturn(null);

            assertThatThrownBy(() -> tagService.getTagById(TAG_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> {
                        BusinessException be = (BusinessException) ex;
                        assertThat(be.getCode()).isEqualTo("TAG_NOT_FOUND");
                    });
        }
    }

    @Nested
    @DisplayName("getHotTags")
    class GetHotTagsTests {

        @Test
        @DisplayName("should return hot tags list")
        void getHotTags_success() {
            Tag tag = buildTag();
            when(tagMapper.selectList(any())).thenReturn(List.of(tag));

            List<TagVO> result = tagService.getHotTags();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Java");
        }

        @Test
        @DisplayName("should return empty list when no hot tags")
        void getHotTags_empty() {
            when(tagMapper.selectList(any())).thenReturn(List.of());

            List<TagVO> result = tagService.getHotTags();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("listTags")
    class ListTagsTests {

        @Test
        @DisplayName("should return paginated tags")
        void listTags_success() {
            Tag tag = buildTag();
            Page<Tag> tagPage = new Page<>(1, 10, 1);
            tagPage.setRecords(List.of(tag));
            when(tagMapper.selectPage(any(Page.class), any())).thenReturn(tagPage);

            Page<TagVO> result = tagService.listTags(1, 10);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getRecords().get(0).name()).isEqualTo("Java");
        }
    }

    @Nested
    @DisplayName("getTrendingTopics")
    class GetTrendingTopicsTests {

        @Test
        @DisplayName("should return cached trending topics when available")
        void getTrendingTopics_cached() {
            TagVO cachedTag = new TagVO(TAG_ID, "Java", "icon", 10, true, 1, null);
            when(communityCacheService.getTrendingTopics(anyInt())).thenReturn(Optional.of(List.of(cachedTag)));

            List<TagVO> result = tagService.getTrendingTopics(10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).name()).isEqualTo("Java");
            verify(tagMapper, never()).selectList(any());
        }

        @Test
        @DisplayName("should query DB and cache result when not cached")
        void getTrendingTopics_notCached() {
            Tag tag = buildTag();
            when(communityCacheService.getTrendingTopics(anyInt())).thenReturn(Optional.empty());
            when(tagMapper.selectList(any())).thenReturn(List.of(tag));

            List<TagVO> result = tagService.getTrendingTopics(10);

            assertThat(result).hasSize(1);
            verify(tagMapper).selectList(any());
            verify(communityCacheService).putTrendingTopics(anyInt(), any());
        }

        @Test
        @DisplayName("should clamp limit to safe bounds")
        void getTrendingTopics_clampLimit() {
            when(communityCacheService.getTrendingTopics(anyInt())).thenReturn(Optional.empty());
            when(tagMapper.selectList(any())).thenReturn(List.of());

            tagService.getTrendingTopics(100);

            verify(communityCacheService).getTrendingTopics(50);
        }
    }
}
