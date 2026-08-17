package com.cloudmart.wish.service;

import com.cloudmart.wish.dto.CreateCategoryRequest;
import com.cloudmart.wish.dto.UpdateCategoryRequest;
import com.cloudmart.wish.vo.CategoryVO;

import java.util.List;

/**
 * 心愿分类服务接口。
 *
 * <p>提供分类字典查询（用户端）和 CRUD（管理后台）。</p>
 */
public interface CategoryService {

    /**
     * 获取全部分类字典（用户端，Redis 缓存）。
     *
     * @return 分类列表，按 sort 升序
     */
    List<CategoryVO> listCategories();

    /**
     * 创建分类（管理后台）。
     *
     * @param request 创建请求
     * @return 创建后的分类 VO
     */
    CategoryVO createCategory(CreateCategoryRequest request);

    /**
     * 更新分类（管理后台）。
     *
     * @param id      分类 ID
     * @param request 更新请求
     * @return 更新后的分类 VO
     */
    CategoryVO updateCategory(Long id, UpdateCategoryRequest request);

    /**
     * 删除分类（管理后台，软删）。
     *
     * @param id 分类 ID
     */
    void deleteCategory(Long id);
}
