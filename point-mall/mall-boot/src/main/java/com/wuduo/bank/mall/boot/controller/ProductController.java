package com.wuduo.bank.mall.boot.controller;

import com.wuduo.bank.mall.api.dto.ProductCreateRequest;
import com.wuduo.bank.mall.api.dto.ProductResponse;
import com.wuduo.bank.mall.application.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Product REST controller
 */
@RestController
@RequestMapping("/api/v1/mall/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody ProductCreateRequest request) {
        return ResponseEntity.ok(productService.update(id, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> page(@RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return ResponseEntity.ok(productService.page(pageNum, pageSize));
    }

    @PutMapping("/{id}/shelf")
    public ResponseEntity<ProductResponse> onOffShelf(@PathVariable Long id,
                                                      @RequestParam Integer status) {
        return ResponseEntity.ok(productService.onOffShelf(id, status));
    }
}
