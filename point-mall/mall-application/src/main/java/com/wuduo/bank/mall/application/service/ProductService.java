package com.wuduo.bank.mall.application.service;

import com.wuduo.bank.mall.api.dto.ProductCreateRequest;
import com.wuduo.bank.mall.api.dto.ProductResponse;
import com.wuduo.bank.mall.api.dto.ProductUpdateRequest;

import java.util.List;

/**
 * Product application service interface
 */
public interface ProductService {

    /**
     * Create a new product
     */
    ProductResponse create(ProductCreateRequest request);

    /**
     * Update an existing product
     */
    ProductResponse update(Long id, ProductUpdateRequest request);

    /**
     * Get product by ID
     */
    ProductResponse getById(Long id);

    /**
     * Paginated product list
     */
    List<ProductResponse> page(Integer pageNum, Integer pageSize, Integer category);

    /**
     * Toggle product on/off shelf
     */
    ProductResponse onOffShelf(Long id, Integer status);
}
