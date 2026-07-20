package com.wuduo.bank.mall.application.service;

import com.wuduo.bank.mall.api.dto.OrderCreateRequest;
import com.wuduo.bank.mall.api.dto.OrderResponse;

import java.util.List;

/**
 * Order application service interface
 */
public interface OrderService {

    /**
     * Create a new order
     */
    OrderResponse create(OrderCreateRequest request);

    /**
     * Get order by ID
     */
    OrderResponse getById(Long id);

    /**
     * Paginated order list
     */
    List<OrderResponse> page(Long userId, Integer pageNum, Integer pageSize);

    /**
     * Cancel an order
     */
    OrderResponse cancel(Long id);
}
