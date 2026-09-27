package com.cloudmart.pet.wallet;

import com.cloudmart.pet.entity.PetWalletAdjustment;

import java.util.List;

/**
 * 宠物币调账服务（W04/§4.1/§4.3-4）。
 *
 * <p>规则（§8.4）：</p>
 * <ul>
 *   <li>申请：操作人取认证上下文（禁止客户端任填）；delta 带符号非 0；reason 必填；</li>
 *   <li>审批：必须与申请人不同的管理员；CAS 防并发双审；审批成功原子入账
 *       （bizType=ADJUSTMENT、bizKey=adjustmentId，事务由钱包 MANDATORY 承接）；</li>
 *   <li>幂等：重复审批返回原结果（不产生第二次资金变动）；</li>
 *   <li>冻结账户允许调账入账（§5.3）；</li>
 *   <li>不提供"直接改 balance"的接口；任何余额变化都有独立流水。</li>
 * </ul>
 */
public interface PetWalletAdjustmentService {

    /** 创建调账申请（申请人=当前管理员上下文） */
    PetWalletAdjustment apply(Long targetUserId, long delta, String reason,
                              String ticketNo, Long operatorAdminId);

    /** 审批通过：另一管理员；原子入账；重复审批返回原记录 */
    PetWalletAdjustment approve(Long adjustmentId, Long operatorAdminId, String reason);

    /** 审批拒绝：另一管理员；重复审批返回原记录 */
    PetWalletAdjustment reject(Long adjustmentId, Long operatorAdminId, String reason);

    /** 按 id 查询 */
    PetWalletAdjustment findById(Long adjustmentId);

    /** 分页查询（状态筛选，新→旧） */
    List<PetWalletAdjustment> list(String status, Long userId, int page, int size);

    /** 冻结/解冻账户（expectedVersion CAS；reason 必填；冻结不阻止退款与调账入账） */
    void setAccountStatus(Long userId, boolean frozen, String reason, Long expectedVersion, Long operatorAdminId);
}
