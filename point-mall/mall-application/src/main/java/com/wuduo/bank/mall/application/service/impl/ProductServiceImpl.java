package com.wuduo.bank.mall.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wuduo.bank.common.exception.BizException;
import com.wuduo.bank.common.model.ErrorCode;
import com.wuduo.bank.mall.api.dto.ProductCreateRequest;
import com.wuduo.bank.mall.api.dto.ProductResponse;
import com.wuduo.bank.mall.api.dto.ProductUpdateRequest;
import com.wuduo.bank.mall.application.service.ProductService;
import com.wuduo.bank.mall.domain.entity.MallProduct;
import com.wuduo.bank.mall.infrastructure.mapper.MallProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Product application service implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final DateTimeFormatter CODE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MallProductMapper mallProductMapper;

    @Override
    public ProductResponse create(ProductCreateRequest request) {
        // validate
        if (request.getTotalStock() == null || request.getTotalStock() <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "总库存必须大于0");
        }
        if (request.getPointPrice() == null || request.getPointPrice() <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "积分价格必须大于0");
        }

        // generate product code
        String productCode = "MALL" + LocalDate.now().format(CODE_DATE_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

        MallProduct product = new MallProduct();
        product.setProductCode(productCode);
        product.setName(request.getName());
        product.setCategory(request.getCategory());
        product.setPointPrice(request.getPointPrice());
        product.setOriginalPrice(request.getOriginalPrice());
        product.setTotalStock(request.getTotalStock());
        product.setAvailableStock(request.getTotalStock());
        product.setStatus(1); // on-shelf by default
        product.setDisplayOrder(0);
        product.setImages(request.getImages());
        product.setDescription(request.getDescription());
        product.setRightsCode(request.getRightsCode());

        mallProductMapper.insert(product);
        log.info("Created product: code={}, name={}", productCode, request.getName());
        return toResponse(product);
    }

    @Override
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        MallProduct product = mallProductMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.MALL_PRODUCT_NOT_FOUND);
        }

        // merge non-null fields
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }
        if (request.getPointPrice() != null) {
            product.setPointPrice(request.getPointPrice());
        }
        if (request.getOriginalPrice() != null) {
            product.setOriginalPrice(request.getOriginalPrice());
        }
        if (request.getTotalStock() != null) {
            int usedStock = product.getTotalStock() - product.getAvailableStock();
            if (request.getTotalStock() < usedStock) {
                throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "总库存不能小于已使用库存");
            }
            int delta = request.getTotalStock() - product.getTotalStock();
            product.setTotalStock(request.getTotalStock());
            product.setAvailableStock(product.getAvailableStock() + delta);
        }
        if (request.getImages() != null) {
            product.setImages(request.getImages());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getDisplayOrder() != null) {
            product.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getRightsCode() != null) {
            product.setRightsCode(request.getRightsCode());
        }

        mallProductMapper.updateById(product);
        log.info("Updated product: id={}", id);
        return toResponse(product);
    }

    @Override
    public ProductResponse getById(Long id) {
        MallProduct product = mallProductMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.MALL_PRODUCT_NOT_FOUND);
        }
        return toResponse(product);
    }

    @Override
    public List<ProductResponse> page(Integer pageNum, Integer pageSize, Integer category) {
        LambdaQueryWrapper<MallProduct> wrapper = new LambdaQueryWrapper<>();
        if (category != null) {
            wrapper.eq(MallProduct::getCategory, category);
        }
        wrapper.orderByDesc(MallProduct::getDisplayOrder)
               .orderByDesc(MallProduct::getId);

        Page<MallProduct> page = new Page<>(pageNum, pageSize);
        Page<MallProduct> result = mallProductMapper.selectPage(page, wrapper);
        return result.getRecords().stream().map(this::toResponse).toList();
    }

    @Override
    public ProductResponse onOffShelf(Long id, Integer status) {
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "状态值非法，仅支持 0(下架) 或 1(上架)");
        }
        MallProduct product = mallProductMapper.selectById(id);
        if (product == null) {
            throw new BizException(ErrorCode.MALL_PRODUCT_NOT_FOUND);
        }
        product.setStatus(status);
        mallProductMapper.updateById(product);
        log.info("Product {} status changed to {}", id, status);
        return toResponse(product);
    }

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private ProductResponse toResponse(MallProduct product) {
        ProductResponse resp = new ProductResponse();
        resp.setId(product.getId());
        resp.setProductCode(product.getProductCode());
        resp.setName(product.getName());
        resp.setCategory(product.getCategory());
        resp.setPointPrice(product.getPointPrice());
        resp.setOriginalPrice(product.getOriginalPrice());
        resp.setTotalStock(product.getTotalStock());
        resp.setAvailableStock(product.getAvailableStock());
        resp.setStatus(product.getStatus());
        resp.setDisplayOrder(product.getDisplayOrder());
        resp.setImages(product.getImages());
        resp.setDescription(product.getDescription());
        resp.setRightsCode(product.getRightsCode());
        return resp;
    }
}
