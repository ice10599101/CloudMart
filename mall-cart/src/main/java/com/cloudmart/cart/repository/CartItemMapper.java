package com.cloudmart.cart.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.cart.entity.CartItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * CART-01：原子加购（DB 权威）——UNIQUE(user_id,sku_id) 冲突时数量原子累加
     * （上限 999），并发加购不丢增量；返回 1 表示写入成功。
     */
    @Insert("INSERT INTO cart_items (user_id, product_id, sku_id, quantity, checked) "
            + "VALUES (#{userId}, #{productId}, #{skuId}, #{quantity}, 1) "
            + "AS new "
            + "ON DUPLICATE KEY UPDATE quantity = LEAST(quantity + new.quantity, 999), checked = 1, updated_at = NOW()")
    int upsertIncrement(@Param("userId") Long userId, @Param("productId") Long productId,
                        @Param("skuId") Long skuId, @Param("quantity") Integer quantity);

    @Select("SELECT * FROM cart_items WHERE user_id = #{userId} AND sku_id = #{skuId}")
    CartItem findByUserAndSku(@Param("userId") Long userId, @Param("skuId") Long skuId);

    /** CART-01：精确清理本次购买的行（下单成功后按 SKU 集合删除，不动无关行） */
    @Update("<script>DELETE FROM cart_items WHERE user_id = #{userId} AND checked = 1 "
            + "AND sku_id IN <foreach collection='skuIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    int deleteCheckedBySkus(@Param("userId") Long userId, @Param("skuIds") java.util.List<Long> skuIds);
}
