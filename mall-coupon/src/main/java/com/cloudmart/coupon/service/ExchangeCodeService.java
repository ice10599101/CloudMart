package com.cloudmart.coupon.service;

import com.cloudmart.coupon.vo.ExchangeCodeVO;
import com.cloudmart.coupon.vo.ExchangeCodeVO.BatchGenerateResult;

import java.util.List;

/**
 * 兑换码服务
 * <p>
 * 提供兑换码批量生成、兑换、查询、作废能力。
 * 生成基于 Redis 原子递增序列 + 加权校验码 + Base32 编码；
 * 兑换基于 Redis BitMap 原子 SETBIT 防重兑 + DB CAS 双重保障。
 * </p>
 */
public interface ExchangeCodeService {

    /**
     * 批量生成兑换码
     *
     * @param templateId 优惠券模板ID
     * @param quantity   生成数量（1-1000）
     * @return 批次号 + 兑换码列表
     */
    BatchGenerateResult generateBatch(Long templateId, int quantity);

    /**
     * 兑换码兑换优惠券
     * <p>
     * 流程：格式校验 → BitMap 原子防重 → DB CAS 状态流转 → 调用优惠券领取。
     * 任一环节失败都会回滚此前所做的变更。
     * </p>
     *
     * @param userId 用户ID
     * @param code   兑换码
     * @return 领取到的用户券ID
     */
    Long exchange(Long userId, String code);

    /**
     * 查询兑换码详情
     *
     * @param code 兑换码
     * @return 兑换码信息
     */
    ExchangeCodeVO getByCode(String code);

    /**
     * 分页查询指定模板的兑换码列表
     *
     * @param templateId 模板ID
     * @param status     状态过滤
     * @param page       页码
     * @param size       每页数量
     * @return 兑换码列表
     */
    List<ExchangeCodeVO> listByTemplate(Long templateId, String status, int page, int size);

    /**
     * 统计指定模板的兑换码数量
     *
     * @param templateId 模板ID
     * @param status     状态过滤
     * @return 数量
     */
    long countByTemplate(Long templateId, String status);

    /**
     * 作废兑换码
     *
     * @param code 兑换码
     */
    void disable(String code);
}
