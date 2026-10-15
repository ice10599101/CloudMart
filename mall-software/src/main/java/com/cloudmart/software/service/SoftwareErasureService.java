package com.cloudmart.software.service;

/**
 * 软件域账号注销（方案 11.3）：幂等可续跑；全部完成返回 true，
 * 未完成抛可重试异常——注销编排据此不得推进 FILE/ERASE。
 */
public interface SoftwareErasureService {

    /**
     * @return true=本域清理完成（DONE 后重复调用亦返回 true）
     * @throws com.cloudmart.common.exception.BusinessException 未完成时抛
     *         SOFTWARE_ERASURE_INCOMPLETE（可重试），编排侧应保留步骤待重试
     */
    boolean eraseUser(Long userId);
}
