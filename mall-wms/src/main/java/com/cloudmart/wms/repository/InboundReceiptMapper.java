package com.cloudmart.wms.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cloudmart.wms.entity.InboundReceipt;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 收货流水 Mapper（T19）：receiptId 幂等插入。 */
@Mapper
public interface InboundReceiptMapper extends BaseMapper<InboundReceipt> {

    /** 幂等登记：uk(receipt_id) 冲突 0 行（重复提交返回原收货） */
    @Insert("INSERT IGNORE INTO inbound_receipt (receipt_id, inbound_order_id, inbound_item_id, "
            + "sku_id, quantity, operator_id, quality_result, biz_source) "
            + "VALUES (#{r.receiptId}, #{r.inboundOrderId}, #{r.inboundItemId}, #{r.skuId}, "
            + "#{r.quantity}, #{r.operatorId}, #{r.qualityResult}, #{r.bizSource})")
    int insertIfAbsent(@Param("r") InboundReceipt receipt);
}
