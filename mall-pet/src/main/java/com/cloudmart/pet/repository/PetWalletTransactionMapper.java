package com.cloudmart.pet.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.pet.entity.PetWalletTransaction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** PetWalletTransactionMapper：MyBatis-Plus BaseMapper（全项目约定：复杂查询用 LambdaWrapper，无 XML）。 */
@Mapper
public interface PetWalletTransactionMapper extends BaseMapper<PetWalletTransaction> {

    /**
     * 按主键加锁读（P01）：退款在核对累计退款前必须锁定原交易行，
     * 防止两个并发退款都读到"已退 0"后各自放行（P03 要求"先锁原交易再核对累计退款"）。
     */
    @Select("SELECT * FROM pet_wallet_transaction WHERE id = #{id} FOR UPDATE")
    PetWalletTransaction selectByIdForUpdate(@Param("id") Long id);
}
