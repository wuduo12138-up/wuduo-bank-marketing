package com.wuduo.bank.mall.api.client;

import com.wuduo.bank.mall.api.dto.OrderCreateRequest;
import com.wuduo.bank.mall.api.dto.OrderResponse;
import com.wuduo.bank.mall.api.dto.ProductCreateRequest;
import com.wuduo.bank.mall.api.dto.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mall Feign client for inter-service communication
 */
@FeignClient(name = "point-mall", contextId = "mallFeignClient")
public interface MallFeignClient {

    @PostMapping("/api/v1/mall/products")
    ProductResponse createProduct(@RequestBody ProductCreateRequest request);

    @GetMapping("/api/v1/mall/products/{id}")
    ProductResponse getProductById(@PathVariable("id") Long id);

    @GetMapping("/api/v1/mall/products")
    List<ProductResponse> listProducts();

    @PostMapping("/api/v1/mall/orders")
    OrderResponse createOrder(@RequestBody OrderCreateRequest request);

    @GetMapping("/api/v1/mall/orders/{id}")
    OrderResponse getOrderById(@PathVariable("id") Long id);
}
