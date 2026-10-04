package com.cloudmart.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.cloudmart.common.exception.BusinessException;
import com.cloudmart.wms.dto.CreateInboundOrderRequest;
import com.cloudmart.wms.dto.InboundItemRequest;
import com.cloudmart.wms.dto.InboundOrderDTO;
import com.cloudmart.wms.dto.InboundOrderItemDTO;
import com.cloudmart.wms.entity.InboundOrder;
import com.cloudmart.wms.entity.InboundOrderItem;
import com.cloudmart.wms.entity.InboundReceipt;
import com.cloudmart.wms.repository.InboundOrderItemMapper;
import com.cloudmart.wms.repository.InboundReceiptMapper;
import com.cloudmart.wms.repository.InboundOrderMapper;
import com.cloudmart.wms.service.InboundOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@lombok.extern.slf4j.Slf4j
public class InboundOrderServiceImpl implements InboundOrderService {

    private final InboundOrderMapper inboundOrderMapper;
    private final InboundOrderItemMapper inboundOrderItemMapper;
    private final InboundReceiptMapper receiptMapper;
    private final com.cloudmart.common.async.outbox.OutboxService outboxService;
    private final com.cloudmart.wms.feign.InventoryRestockFeignClient inventoryRestockFeignClient;
    private final com.cloudmart.common.async.compensation.CompensationTaskService compensationTaskService;

    public InboundOrderServiceImpl(InboundOrderMapper inboundOrderMapper,
                                    InboundOrderItemMapper inboundOrderItemMapper,
                                    InboundReceiptMapper receiptMapper,
                                    com.cloudmart.common.async.outbox.OutboxService outboxService,
                                    com.cloudmart.wms.feign.InventoryRestockFeignClient inventoryRestockFeignClient,
                                    com.cloudmart.common.async.compensation.CompensationTaskService compensationTaskService) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundOrderItemMapper = inboundOrderItemMapper;
        this.receiptMapper = receiptMapper;
        this.outboxService = outboxService;
        this.inventoryRestockFeignClient = inventoryRestockFeignClient;
        this.compensationTaskService = compensationTaskService;
    }

    @Override
    @Transactional
    public InboundOrderDTO createInboundOrder(CreateInboundOrderRequest request) {
        InboundOrder order = new InboundOrder();
        order.setWarehouseId(request.warehouseId());
        order.setType(request.type());
        order.setReferenceNo(request.referenceNo());
        order.setStatus("PENDING");
        order.setTotalQuantity(request.items().stream().mapToInt(InboundItemRequest::expectedQuantity).sum());
        order.setReceivedQuantity(0);
        order.setRemark(request.remark());
        inboundOrderMapper.insert(order);

        for (InboundItemRequest item : request.items()) {
            InboundOrderItem entity = new InboundOrderItem();
            entity.setInboundOrderId(order.getId());
            entity.setSkuId(item.skuId());
            entity.setProductName(item.productName());
            entity.setExpectedQuantity(item.expectedQuantity());
            entity.setReceivedQuantity(0);
            entity.setLocationCode(item.locationCode());
            inboundOrderItemMapper.insert(entity);
        }

        return toDTO(order);
    }

    @Override
    @Transactional
    public InboundOrderDTO receiveItem(Long inboundOrderId, Long itemId, Integer receivedQuantity) {
        // 兼容旧签名：自动生成 receiptId（调用方重试须携带原 receiptId 才幂等）
        return receiveItem(inboundOrderId, itemId, receivedQuantity,
                "RCP" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 20),
                null, InboundReceipt.QUALITY_PASSED, InboundReceipt.SOURCE_PURCHASE);
    }

    /**
     * T19 收货：receiptId 幂等（重复提交返回原收货）+ 正数校验 + 行级 CAS 防超收/
     * 并发丢数 + 收货流水事实 + 库存入账（提交后调用，失败登记补偿）。
     *
     * <p>同一 receiptId 重试不加倍；两个仓管并发不丢数量（单行 CAS 累加）；
     * 超收明确拒绝（received + delta ≤ expected）；隔离验收不入可售。</p>
     */
    @Override
    @Transactional
    public InboundOrderDTO receiveItem(Long inboundOrderId, Long itemId, Integer receivedQuantity,
                                       String receiptId, Long operatorId,
                                       String qualityResult, String bizSource) {
        if (receivedQuantity == null || receivedQuantity <= 0) {
            throw new BusinessException("RECEIVE_QUANTITY_INVALID", "收货数量必须为正数");
        }
        if (receiptId == null || receiptId.isBlank() || receiptId.length() > 64) {
            throw new BusinessException("RECEIPT_ID_INVALID", "收货流水ID缺失或非法");
        }
        String quality = InboundReceipt.QUALITY_QUARANTINE.equals(qualityResult)
                ? InboundReceipt.QUALITY_QUARANTINE : InboundReceipt.QUALITY_PASSED;
        String source = InboundReceipt.SOURCE_AFTER_SALE.equals(bizSource)
                ? InboundReceipt.SOURCE_AFTER_SALE : InboundReceipt.SOURCE_PURCHASE;

        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("INBOUND_ORDER_NOT_FOUND", "入库单不存在");
        }
        if ("COMPLETED".equals(order.getStatus())) {
            throw new BusinessException("INBOUND_COMPLETED", "入库单已完成");
        }

        InboundOrderItem item = inboundOrderItemMapper.selectById(itemId);
        if (item == null || !item.getInboundOrderId().equals(inboundOrderId)) {
            throw new BusinessException("ITEM_NOT_FOUND", "入库明细不存在");
        }

        // T19 幂等：receiptId 唯一键——重复提交返回原收货，不二次累计
        InboundReceipt receipt = new InboundReceipt();
        receipt.setReceiptId(receiptId);
        receipt.setInboundOrderId(inboundOrderId);
        receipt.setInboundItemId(itemId);
        receipt.setSkuId(item.getSkuId());
        receipt.setQuantity(receivedQuantity);
        receipt.setOperatorId(operatorId);
        receipt.setQualityResult(quality);
        receipt.setBizSource(source);
        if (receiptMapper.insertIfAbsent(receipt) == 0) {
            log.info("[T19] 收货流水重放（幂等跳过） receiptId={} inboundOrderId={}",
                    receiptId, inboundOrderId);
            return toDTO(order);
        }

        // T19 行级 CAS：received + delta ≤ expected（防超收）且基于当前值累加（防并发丢数）
        int updated = inboundOrderItemMapper.receiveIncrement(itemId, receivedQuantity);
        if (updated == 0) {
            throw new BusinessException("RECEIVE_OVER_RECEIVED",
                    "收货数量超过待收余量（已收 " + item.getReceivedQuantity() + "/计划 "
                            + item.getExpectedQuantity() + "）");
        }

        order.setStatus("RECEIVING");
        order.setReceivedQuantity(order.getReceivedQuantity() + receivedQuantity);
        inboundOrderMapper.updateById(order);

        // T19 库存入账：仅 PASSED 可售入库；提交后调用（远程不入收货事务），
        // 失败登记持久化补偿（receiptId 幂等保证重试不加倍）
        if (InboundReceipt.QUALITY_PASSED.equals(quality)) {
            String payload = "{\"receiptId\":\"" + receiptId + "\",\"skuId\":" + item.getSkuId()
                    + ",\"quantity\":" + receivedQuantity + "}";
            registerRestockAfterCommit(receiptId, item.getSkuId(), receivedQuantity, payload);
        }
        log.info("[T19] 收货登记 inboundOrderId={} itemId={} quantity={} receiptId={} quality={}",
                inboundOrderId, itemId, receivedQuantity, receiptId, quality);
        return toDTO(order);
    }

    /** T19：库存入账在收货事务提交后执行；失败登记补偿任务（receiptId 幂等重试安全）。 */
    private void registerRestockAfterCommit(String receiptId, Long skuId, int quantity, String payload) {
        Runnable restock = () -> {
            try {
                var resp = inventoryRestockFeignClient.restock(
                        new com.cloudmart.wms.feign.InventoryRestockFeignClient.RestockRequest(
                                receiptId, skuId, quantity));
                if (resp == null || !resp.success()) {
                    throw new IllegalStateException(resp == null ? "empty response" : "success=false");
                }
                log.info("[T19] 库存入账完成 receiptId={} skuId={} quantity={}", receiptId, skuId, quantity);
            } catch (Exception ex) {
                log.error("[T19] 库存入账失败（已登记补偿） receiptId={} skuId={}: {}",
                        receiptId, skuId, ex.getMessage());
                compensationTaskService.createIfAbsent("inventory-restock:" + receiptId,
                        "inventory-restock", receiptId, payload);
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            restock.run();
                        }
                    });
        } else {
            restock.run();
        }
    }

    /**
     * T19 完成收货：全部必需明细收齐才允许 COMPLETED（缺量明确拒绝——差异收尾
     * 属显式授权流程，不默认放行）；状态 CAS PENDING/RECEIVING → COMPLETED。
     */
    @Override
    @Transactional
    public InboundOrderDTO completeInbound(Long inboundOrderId) {
        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("INBOUND_ORDER_NOT_FOUND", "入库单不存在");
        }
        if ("COMPLETED".equals(order.getStatus())) {
            return toDTO(order);
        }
        var items = inboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<InboundOrderItem>()
                        .eq(InboundOrderItem::getInboundOrderId, inboundOrderId));
        for (InboundOrderItem item : items) {
            int received = item.getReceivedQuantity() == null ? 0 : item.getReceivedQuantity();
            if (received < item.getExpectedQuantity()) {
                throw new BusinessException("INBOUND_SHORTAGE",
                        "明细未收齐（SKU " + item.getSkuId() + " 已收 " + received
                                + "/计划 " + item.getExpectedQuantity() + "），缺量收尾需差异审批");
            }
        }
        int updated = inboundOrderMapper.markCompleted(inboundOrderId);
        if (updated == 0) {
            log.info("[T19] 入库单状态已变更（CAS 未命中，幂等返回） inboundOrderId={}", inboundOrderId);
        }
        return toDTO(inboundOrderMapper.selectById(inboundOrderId));
    }

    @Override
    public InboundOrderDTO getInboundOrder(Long inboundOrderId) {
        InboundOrder order = inboundOrderMapper.selectById(inboundOrderId);
        if (order == null) {
            throw new BusinessException("INBOUND_ORDER_NOT_FOUND", "入库单不存在");
        }
        return toDTO(order);
    }

    @Override
    public InboundOrderDTO findByTypeAndReferenceNo(String type, String referenceNo) {
        InboundOrder order = inboundOrderMapper.selectOne(new LambdaQueryWrapper<InboundOrder>()
                .eq(InboundOrder::getType, type)
                .eq(InboundOrder::getReferenceNo, referenceNo)
                .last("LIMIT 1"));
        return order == null ? null : toDTO(order);
    }

    @Override
    public IPage<InboundOrderDTO> listInboundOrders(String status, Long warehouseId, int page, int size) {
        LambdaQueryWrapper<InboundOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(InboundOrder::getStatus, status);
        }
        if (warehouseId != null) {
            wrapper.eq(InboundOrder::getWarehouseId, warehouseId);
        }
        wrapper.orderByDesc(InboundOrder::getCreatedAt);
        IPage<InboundOrder> pageResult = inboundOrderMapper.selectPage(new Page<>(page, size), wrapper);
        Page<InboundOrderDTO> dtoPage = new Page<>(pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
        dtoPage.setRecords(pageResult.getRecords().stream().map(this::toDTO).toList());
        return dtoPage;
    }

    private InboundOrderDTO toDTO(InboundOrder entity) {
        List<InboundOrderItem> items = inboundOrderItemMapper.selectList(
            new LambdaQueryWrapper<InboundOrderItem>().eq(InboundOrderItem::getInboundOrderId, entity.getId())
        );
        List<InboundOrderItemDTO> itemDTOs = items.stream()
            .map(i -> new InboundOrderItemDTO(i.getId(), i.getSkuId(), i.getProductName(),
                i.getExpectedQuantity(), i.getReceivedQuantity(), i.getLocationCode()))
            .toList();
        return new InboundOrderDTO(
            entity.getId(), entity.getWarehouseId(), entity.getType(),
            entity.getReferenceNo(), entity.getStatus(), entity.getTotalQuantity(),
            entity.getReceivedQuantity(), entity.getOperatorUserId(),
            entity.getCompletedTime(), entity.getRemark(), entity.getCreatedAt(), itemDTOs
        );
    }
}
