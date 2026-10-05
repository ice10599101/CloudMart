package com.cloudmart.file.service;

/**
 * 账号数据擦除服务（T06 注销编排，mall-user 经 Feign 调用）。
 */
public interface AccountErasureService {

    /** 处置用户文件资产（未引用删除/被引用匿名化）；幂等 */
    boolean eraseUserData(Long userId);
}
