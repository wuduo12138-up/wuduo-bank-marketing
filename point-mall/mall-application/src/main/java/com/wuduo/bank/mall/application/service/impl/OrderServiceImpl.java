package com.wuduo.bank.mall.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import com.wuduo.bank.mall.api.dto.OrderCreateRequest;
import com.wuduo.bank.mall.api.dto.OrderResponse;
import com.wuduo.bank.mall.api.enums.OrderStatus;
import com.wuduo.bank.mall.api.enums.ProductCategory;
import com.wuduo.bank.mall.application.service.OrderService;
import com.wuduo.bank.mall.domain.entity.MallOrder;
import com.wuduo.bank.mall.domain.entity.MallProduct;
import com.wuduo.bank.mall.domain.entity.MallStockLog;
import com.wuduo.bank.mall.infrastructure.mapper.MallOrderMapper;
import com.wuduo.bank.mall.infrastructure.mapper.MallProductMapper;
import com.wuduo.bank.mall.infrastructure.mapper.MallStockLogMapper;
import com.wuduo.bank.mall.infrastructure.util.DistributedLockHelper;
import com.wuduo.bank.point.api.client.PointFeignClient;
import com.wuduo.bank.point.api.dto.PointDeductRequest;
import com.wuduo.bank.point.api.dto.PointFreezeRequest;
import com.wuduo.bank.point.api.dto.PointFreezeResponse;
import com.wuduo.bank.rights.api.client.RightsFeignClient;
import com.wuduo.bank.rights.api.dto.RightsInstanceResponse;
import com.wuduo.bank.rights.api.dto.RightsIssueRequest;
import com.wuduo.bank.rights.api.enums.RightsSourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Order application service implementation.
 *
 * <p>Exchange flow (sync with compensation):
 * <ol>
 *   <li>Validate product (exists + on-shelf)</li>
 *   <li>Atomic stock deduction (MySQL)</li>
 *   <li>Create order (PENDING)</li>
 *   <li>Freeze points (Feign → point-svc)</li>
 *   <li>Deduct points (Feign → point-svc, from freeze)</li>
 *   <li>Record stock log</li>
 *   <li>Issue rights if RIGHTS-category (Feign → rights-svc)</li>
 *   <li>Update order to EXCHANGED</li>
 * </ol>
 * On failure at any step: compensate previous steps.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MallProductMapper mallProductMapper;
    private final MallOrderMapper mallOrderMapper;
    private final MallStockLogMapper mallStockLogMapper;
    private final DistributedLockHelper lockHelper;
    private final PointFeignClient pointFeignClient;
    private final RightsFeignClient rightsFeignClient;

    @Override
    public OrderResponse create(OrderCreateRequest request) {
        // validate input
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "兑换数量必须大于0");
        }

        // distributed lock by userId
        return lockHelper.executeWithLock(request.getUserId(), () -> doCreate(request));
    }

    private OrderResponse doCreate(OrderCreateRequest request) {
        Long userId = request.getUserId();

        // 1. Load and validate product
        MallProduct product = mallProductMapper.selectOne(
                new LambdaQueryWrapper<MallProduct>()
                        .eq(MallProduct::getProductCode, request.getProductCode()));
        if (product == null) {
            throw new BizException(ErrorCode.MALL_PRODUCT_NOT_FOUND);
        }
        if (product.getStatus() != 1) {
            throw new BizException(ErrorCode.MALL_PRODUCT_OFF_SHELF);
        }

        // 2. Generate order no and calculate points
        String orderNo = "MO" + LocalDateTime.now().format(ORDER_DATE_FMT)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
        long totalPoints = product.getPointPrice() * request.getQuantity();
        String freezeBizNo = "FZ" + orderNo;

        // 3. Create order (PENDING)
        MallOrder order = new MallOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductCode(product.getProductCode());
        order.setProductName(product.getName());
        order.setPointAmount(totalPoints);
        order.setQuantity(request.getQuantity());
        order.setStatus(OrderStatus.PENDING.getCode());
        order.setDeliveryInfo(request.getDeliveryInfo());
        order.setRightsCode(product.getRightsCode());
        mallOrderMapper.insert(order);

        // 4. Atomic stock deduction
        boolean stockDeducted = deductStock(product.getId(), request.getQuantity());
        if (!stockDeducted) {
            mallOrderMapper.deleteById(order.getId()); // clean up pending order
            throw new BizException(ErrorCode.MALL_STOCK_INSUFFICIENT);
        }

        // 5. Freeze points (Feign → point-svc)
        PointFreezeResponse freezeResp;
        try {
            PointFreezeRequest freezeReq = new PointFreezeRequest();
            freezeReq.setUserId(userId);
            freezeReq.setFreezeAmount(totalPoints);
            freezeReq.setBizNo(freezeBizNo);
            freezeResp = pointFeignClient.freeze(freezeReq);
        } catch (Exception e) {
            log.error("Freeze points failed for orderNo={}", orderNo, e);
            rollbackStock(product.getId(), request.getQuantity());
            mallOrderMapper.deleteById(order.getId());
            throw new BizException(ErrorCode.MALL_POINT_OPERATION_FAILED);
        }

        // Check freeze result (point-svc returns HTTP 200 + null fields on biz error)
        if (freezeResp == null || freezeResp.getFreezeNo() == null) {
            log.error("Freeze points returned null freezeNo for orderNo={}", orderNo);
            rollbackStock(product.getId(), request.getQuantity());
            mallOrderMapper.deleteById(order.getId());
            throw new BizException(ErrorCode.MALL_POINT_OPERATION_FAILED);
        }

        // 6. Record stock log
        int beforeStock = product.getAvailableStock();
        int afterStock = beforeStock - request.getQuantity();
        saveStockLog(product.getProductCode(), 1, -request.getQuantity(),
                beforeStock, afterStock, orderNo, null);

        // 7. Deduct points from freeze (Feign → point-svc, idempotent by bizNo=orderNo)
        try {
            PointDeductRequest deductReq = new PointDeductRequest();
            deductReq.setUserId(userId);
            deductReq.setPointAmount(totalPoints);
            deductReq.setType(4); // MALL_EXCHANGE
            deductReq.setBizNo(orderNo);
            deductReq.setFreezeNo(freezeResp.getFreezeNo());
            pointFeignClient.deduct(deductReq);
        } catch (Exception e) {
            log.error("Deduct points failed for orderNo={}", orderNo, e);
            unfreezePoints(userId, freezeBizNo);
            rollbackStock(product.getId(), request.getQuantity());
            mallOrderMapper.deleteById(order.getId());
            throw new BizException(ErrorCode.MALL_POINT_OPERATION_FAILED);
        }

        // 8. Issue rights if RIGHTS-category product
        if (product.getCategory() != null
                && product.getCategory().equals(ProductCategory.RIGHTS.getCode())
                && product.getRightsCode() != null) {
            try {
                RightsIssueRequest rightsReq = new RightsIssueRequest();
                rightsReq.setRightsCode(product.getRightsCode());
                rightsReq.setUserId(String.valueOf(userId));
                rightsReq.setSourceType(RightsSourceType.MALL_EXCHANGE.getCode());
                rightsReq.setSourceNo(orderNo);
                RightsInstanceResponse rightsResp = rightsFeignClient.issue(rightsReq);
                if (rightsResp != null && rightsResp.getInstanceNo() != null) {
                    order.setRightsInstanceNo(rightsResp.getInstanceNo());
                } else {
                    log.error("Rights issue returned null instanceNo for orderNo={}", orderNo);
                }
            } catch (Exception e) {
                // Do NOT throw — points already deducted, manual compensation needed
                log.error("Rights issuance failed for orderNo={}. Points already deducted!", orderNo, e);
            }
        }

        // 9. Update order to EXCHANGED
        order.setStatus(OrderStatus.EXCHANGED.getCode());
        mallOrderMapper.updateById(order);

        log.info("Exchange completed: orderNo={} userId={} productCode={} points={}",
                orderNo, userId, product.getProductCode(), totalPoints);
        return toResponse(order);
    }

    @Override
    public OrderResponse getById(Long id) {
        MallOrder order = mallOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.MALL_ORDER_NOT_FOUND);
        }
        return toResponse(order);
    }

    @Override
    public List<OrderResponse> page(Long userId, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>()
                .eq(MallOrder::getUserId, userId)
                .orderByDesc(MallOrder::getCreatedAt);

        Page<MallOrder> page = new Page<>(pageNum, pageSize);
        Page<MallOrder> result = mallOrderMapper.selectPage(page, wrapper);
        return result.getRecords().stream().map(this::toResponse).toList();
    }

    @Override
    public OrderResponse cancel(Long id) {
        MallOrder order = mallOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.MALL_ORDER_NOT_FOUND);
        }

        // Only PENDING orders can be cancelled
        if (!order.getStatus().equals(OrderStatus.PENDING.getCode())) {
            throw new BizException(ErrorCode.MALL_ORDER_STATUS_INVALID,
                    "仅待处理状态的订单可以取消，当前状态: " + order.getStatus());
        }

        // Rollback stock
        rollbackStockByProductCode(order.getProductCode(), order.getQuantity());

        // Unfreeze points
        String freezeBizNo = "FZ" + order.getOrderNo();
        unfreezePoints(order.getUserId(), freezeBizNo);

        // Update order status
        order.setStatus(OrderStatus.CANCELLED.getCode());
        mallOrderMapper.updateById(order);

        // Record stock log (restore)
        saveStockLog(order.getProductCode(), 2, order.getQuantity(),
                0, 0, order.getOrderNo(), "订单取消恢复库存");

        log.info("Cancelled order: orderNo={}", order.getOrderNo());
        return toResponse(order);
    }

    // -------------------------------------------------------
    // Compensation helpers
    // -------------------------------------------------------

    /**
     * Atomic stock deduction: UPDATE SET available_stock = available_stock - quantity
     * WHERE id = ? AND available_stock >= quantity
     */
    private boolean deductStock(Long productId, int quantity) {
        int updated = mallProductMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MallProduct>()
                        .eq(MallProduct::getId, productId)
                        .setSql("available_stock = available_stock - " + quantity)
                        .ge(MallProduct::getAvailableStock, quantity));
        return updated > 0;
    }

    /**
     * Rollback stock: available_stock = available_stock + quantity
     */
    private void rollbackStock(Long productId, int quantity) {
        mallProductMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MallProduct>()
                        .eq(MallProduct::getId, productId)
                        .setSql("available_stock = available_stock + " + quantity));
    }

    /**
     * Rollback stock by product code (used in cancel flow)
     */
    private void rollbackStockByProductCode(String productCode, int quantity) {
        MallProduct product = mallProductMapper.selectOne(
                new LambdaQueryWrapper<MallProduct>()
                        .eq(MallProduct::getProductCode, productCode));
        if (product != null) {
            rollbackStock(product.getId(), quantity);
        }
    }

    /**
     * Unfreeze points via Feign
     */
    private void unfreezePoints(Long userId, String freezeBizNo) {
        try {
            PointFreezeRequest unfreezeReq = new PointFreezeRequest();
            unfreezeReq.setUserId(userId);
            unfreezeReq.setBizNo(freezeBizNo);
            pointFeignClient.unfreeze(unfreezeReq);
            log.info("Unfroze points for userId={}, bizNo={}", userId, freezeBizNo);
        } catch (Exception e) {
            log.error("Failed to unfreeze points for userId={}, bizNo={}", userId, freezeBizNo, e);
        }
    }

    /**
     * Record stock change log
     */
    private void saveStockLog(String productCode, int changeType, int changeQuantity,
                              int beforeStock, int afterStock, String orderNo, String remark) {
        MallStockLog stockLog = new MallStockLog();
        stockLog.setProductCode(productCode);
        stockLog.setChangeType(changeType);
        stockLog.setChangeQuantity(changeQuantity);
        stockLog.setBeforeStock(beforeStock);
        stockLog.setAfterStock(afterStock);
        stockLog.setOrderNo(orderNo);
        stockLog.setRemark(remark);
        mallStockLogMapper.insert(stockLog);
    }

    // -------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------

    private OrderResponse toResponse(MallOrder order) {
        OrderResponse resp = new OrderResponse();
        resp.setId(order.getId());
        resp.setOrderNo(order.getOrderNo());
        resp.setUserId(order.getUserId());
        resp.setProductCode(order.getProductCode());
        resp.setProductName(order.getProductName());
        resp.setPointAmount(order.getPointAmount());
        resp.setQuantity(order.getQuantity());
        resp.setStatus(order.getStatus());
        resp.setDeliveryInfo(order.getDeliveryInfo());
        resp.setRightsCode(order.getRightsCode());
        resp.setRightsInstanceNo(order.getRightsInstanceNo());
        resp.setCreatedAt(order.getCreatedAt());
        return resp;
    }
}
