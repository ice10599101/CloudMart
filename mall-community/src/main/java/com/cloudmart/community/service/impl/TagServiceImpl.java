package com.cloudmart.community.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.community.dto.CreateTagRequest;
import com.cloudmart.community.dto.UpdateTagRequest;
import com.cloudmart.community.entity.Tag;
import com.cloudmart.community.repository.TagMapper;
import com.cloudmart.community.service.CommunityCacheService;
import com.cloudmart.community.service.TagService;
import com.cloudmart.community.vo.TagVO;
import com.cloudmart.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final CommunityCacheService communityCacheService;

    public TagServiceImpl(TagMapper tagMapper, CommunityCacheService communityCacheService) {
        this.tagMapper = tagMapper;
        this.communityCacheService = communityCacheService;
    }

    @Override
    @Transactional
    public TagVO createTag(CreateTagRequest request) {
        Long existing = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>().eq(Tag::getName, request.name())
        );
        if (existing > 0) {
            throw new BusinessException("TAG_NAME_DUPLICATE", "标签名称已存在");
        }

        Tag tag = new Tag();
        tag.setName(request.name());
        tag.setIcon(request.icon());
        tag.setPostCount(0);
        tag.setIsHot(false);
        tag.setStatus(1);
        tagMapper.insert(tag);

        return convertToVO(tag);
    }

    @Override
    @Transactional
    public TagVO updateTag(Long tagId, UpdateTagRequest request) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("TAG_NOT_FOUND", "标签不存在");
        }

        if (request.name() != null) {
            tag.setName(request.name());
        }
        if (request.icon() != null) {
            tag.setIcon(request.icon());
        }
        if (request.status() != null) {
            tag.setStatus(request.status());
        }
        tagMapper.updateById(tag);

        return convertToVO(tag);
    }

    @Override
    @Transactional
    public void deleteTag(Long tagId) {
        tagMapper.deleteById(tagId);
    }

    @Override
    public List<TagVO> getHotTags() {
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getIsHot, true)
                        .eq(Tag::getStatus, 1)
                        .orderByDesc(Tag::getPostCount)
                        .last("LIMIT 20")
        );
        return tags.stream().map(this::convertToVO).toList();
    }

    @Override
    public Page<TagVO> listTags(int page, int size) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .orderByDesc(Tag::getPostCount);

        Page<Tag> tagPage = tagMapper.selectPage(new Page<>(page, size), wrapper);

        List<TagVO> voList = tagPage.getRecords().stream().map(this::convertToVO).toList();

        Page<TagVO> resultPage = new Page<>(tagPage.getCurrent(), tagPage.getSize(), tagPage.getTotal());
        resultPage.setRecords(voList);
        return resultPage;
    }

    @Override
    public TagVO getTagById(Long tagId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("TAG_NOT_FOUND", "标签不存在");
        }
        return convertToVO(tag);
    }

    @Override
    public List<TagVO> getTrendingTopics(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);

        var cached = communityCacheService.getTrendingTopics(safeLimit);
        if (cached.isPresent()) {
            return cached.get();
        }

        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getStatus, 1)
                        .gt(Tag::getPostCount, 0)
                        .orderByDesc(Tag::getPostCount)
                        .last("LIMIT " + safeLimit)
        );
        List<TagVO> result = tags.stream().map(this::convertToVO).toList();
        communityCacheService.putTrendingTopics(safeLimit, result);
        return result;
    }

    private TagVO convertToVO(Tag tag) {
        return new TagVO(
                tag.getId(),
                tag.getName(),
                tag.getIcon(),
                tag.getPostCount(),
                tag.getIsHot(),
                tag.getStatus(),
                tag.getCreatedAt()
        );
    }

    @Override
    @Transactional
    public void updateTagStatus(Long tagId, Integer status) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("TAG_NOT_FOUND", "标签不存在");
        }
        tag.setStatus(status);
        tagMapper.updateById(tag);
    }
}
