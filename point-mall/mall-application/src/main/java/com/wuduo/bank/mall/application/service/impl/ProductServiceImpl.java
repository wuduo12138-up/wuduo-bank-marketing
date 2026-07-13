package com.wuduo.bank.mall.application.service.impl;

import com.wuduo.bank.mall.api.dto.ProductCreateRequest;
import com.wuduo.bank.mall.api.dto.ProductResponse;
import com.wuduo.bank.mall.application.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Product application service implementation
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    // TODO: Inject repository/mapper dependencies

    @Override
    public ProductResponse create(ProductCreateRequest request) {
        // TODO: Implement product creation logic
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ProductResponse update(Long id, ProductCreateRequest request) {
        // TODO: Implement product update logic
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ProductResponse getById(Long id) {
        // TODO: Implement get product by ID logic
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public List<ProductResponse> page(Integer pageNum, Integer pageSize) {
        // TODO: Implement paginated product list logic
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ProductResponse onOffShelf(Long id, Integer status) {
        // TODO: Implement product on/off shelf logic
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
