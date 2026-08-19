package com.cloudmart.wish.enums;

/**
 * 同意动作（文档 1.2 节 ⑳）：GRANT 同意 / WITHDRAW 撤回。
 *
 * <p>有效性判定取最新一条记录的 action（文档 34.2：用户可随时撤回同意）。</p>
 */
public enum ConsentAction {
    GRANT,
    WITHDRAW
}
