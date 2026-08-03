package com.cloudmart.coupon.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.coupon.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {

    @Update("UPDATE user_coupons SET status = #{targetStatus}, order_id = #{orderId}, "
            + "used_at = #{usedAt}, updated_at = NOW() "
            + "WHERE id = #{userCouponId} AND status = #{expectedStatus}")
    int updateStatusIfMatch(@Param("userCouponId") Long userCouponId,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("targetStatus") String targetStatus,
                            @Param("orderId") Long orderId,
                            @Param("usedAt") LocalDateTime usedAt);

    @Update("UPDATE user_coupons SET status = #{targetStatus}, order_id = NULL, "
            + "used_at = NULL, updated_at = NOW() "
            + "WHERE id = #{userCouponId} AND status = #{expectedStatus} AND order_id = #{orderId}")
    int returnCouponIfMatch(@Param("userCouponId") Long userCouponId,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("targetStatus") String targetStatus,
                            @Param("orderId") Long orderId);

    @Update("UPDATE user_coupons SET status = #{targetStatus}, updated_at = NOW() "
            + "WHERE id = #{userCouponId} AND status = #{expectedStatus}")
    int expireIfMatch(@Param("userCouponId") Long userCouponId,
                      @Param("expectedStatus") String expectedStatus,
                      @Param("targetStatus") String targetStatus);

    @Update("UPDATE user_coupons SET status = 'EXPIRED', updated_at = NOW() "
            + "WHERE status = 'UNUSED' AND expired_at IS NOT NULL AND expired_at < NOW()")
    int batchExpireUnused();
}
