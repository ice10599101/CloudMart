package com.cloudmart.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cloudmart.admin.dto.AdminPostRequest;
import com.cloudmart.admin.dto.AdminPostResponse;
import com.cloudmart.admin.entity.AdminPost;
import com.cloudmart.admin.repository.AdminPostMapper;
import com.cloudmart.admin.service.AdminPostService;
import com.cloudmart.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminPostServiceImpl implements AdminPostService {

    private final AdminPostMapper adminPostMapper;

    public AdminPostServiceImpl(AdminPostMapper adminPostMapper) {
        this.adminPostMapper = adminPostMapper;
    }

    @Override
    public List<AdminPostResponse> list() {
        return adminPostMapper.selectList(
                new LambdaQueryWrapper<AdminPost>().orderByAsc(AdminPost::getOrderNum)
        ).stream().map(this::toResponse).toList();
    }

    @Override
    public AdminPostResponse getById(Long id) {
        AdminPost post = adminPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("POSITION_NOT_FOUND", "岗位不存在");
        }
        return toResponse(post);
    }

    @Override
    @Transactional
    public void create(AdminPostRequest request) {
        checkPostCodeUnique(request.postCode(), null);

        AdminPost post = new AdminPost();
        post.setPostCode(request.postCode());
        post.setPostName(request.postName());
        post.setOrderNum(request.orderNum());
        post.setStatus(request.status() != null ? request.status() : 0);
        post.setRemark(request.remark());
        adminPostMapper.insert(post);
    }

    @Override
    @Transactional
    public void update(Long id, AdminPostRequest request) {
        AdminPost post = adminPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("POSITION_NOT_FOUND", "岗位不存在");
        }

        checkPostCodeUnique(request.postCode(), id);

        post.setPostCode(request.postCode());
        post.setPostName(request.postName());
        post.setOrderNum(request.orderNum());
        post.setStatus(request.status());
        post.setRemark(request.remark());
        adminPostMapper.updateById(post);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        AdminPost post = adminPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("POSITION_NOT_FOUND", "岗位不存在");
        }
        adminPostMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminPost post = adminPostMapper.selectById(id);
        if (post == null) {
            throw new BusinessException("POSITION_NOT_FOUND", "岗位不存在");
        }
        post.setStatus(status);
        adminPostMapper.updateById(post);
    }

    private void checkPostCodeUnique(String postCode, Long excludeId) {
        LambdaQueryWrapper<AdminPost> wrapper = new LambdaQueryWrapper<AdminPost>()
                .eq(AdminPost::getPostCode, postCode);
        AdminPost existing = adminPostMapper.selectOne(wrapper);
        if (existing != null && !existing.getId().equals(excludeId)) {
            throw new BusinessException("POST_CODE_EXISTS", "岗位编码已存在");
        }
    }

    private AdminPostResponse toResponse(AdminPost post) {
        return new AdminPostResponse(
                post.getId(),
                post.getPostCode(),
                post.getPostName(),
                post.getOrderNum(),
                post.getStatus(),
                post.getRemark(),
                post.getCreatedAt()
        );
    }
}
