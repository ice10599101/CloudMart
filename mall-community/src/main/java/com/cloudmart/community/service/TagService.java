package com.cloudmart.community.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateTagRequest;
import com.cloudmart.community.dto.UpdateTagRequest;
import com.cloudmart.community.vo.TagVO;

import java.util.List;

public interface TagService {

    TagVO createTag(CreateTagRequest request);

    TagVO updateTag(Long tagId, UpdateTagRequest request);

    void deleteTag(Long tagId);

    List<TagVO> getHotTags();

    Page<TagVO> listTags(int page, int size);

    TagVO getTagById(Long tagId);

    List<TagVO> getTrendingTopics(int limit);

    void updateTagStatus(Long tagId, Integer status);
}
