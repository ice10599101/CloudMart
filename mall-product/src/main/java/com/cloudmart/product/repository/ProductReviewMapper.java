package com.cloudmart.product.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.product.entity.ProductReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ProductReviewMapper extends BaseMapper<ProductReview> {

    @Select("SELECT rating, COUNT(*) AS cnt FROM product_reviews WHERE product_id = #{productId} AND status = 1 GROUP BY rating")
    List<Map<String, Object>> selectRatingStats(Long productId);

    /**
     * 批量查询多个商品的平均评分，仅统计 status=1 的有效评论。
     * 返回每行包含 productId 与 avgRating 字段。
     */
    @Select({
            "<script>",
            "SELECT product_id AS productId, AVG(rating) AS avgRating",
            "FROM product_reviews",
            "WHERE status = 1 AND product_id IN",
            "<foreach collection='productIds' item='pid' open='(' separator=',' close=')'>#{pid}</foreach>",
            "GROUP BY product_id",
            "</script>"
    })
    List<Map<String, Object>> selectAvgRatingByProductIds(@Param("productIds") List<Long> productIds);
}
