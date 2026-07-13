package com.wuduo.bank.point.api.client;

import com.wuduo.bank.point.api.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

/**
 * Point Feign client for inter-service communication
 */
@FeignClient(name = "point", contextId = "pointFeignClient")
public interface PointFeignClient {

    /**
     * Issue points to a user
     *
     * @param request point issue request
     * @return point account response
     */
    @PostMapping("/api/v1/point/accounts/issue")
    PointAccountResponse issue(@Valid @RequestBody PointIssueRequest request);

    /**
     * Freeze points for a user
     *
     * @param request point freeze request
     * @return point freeze response
     */
    @PostMapping("/api/v1/point/accounts/freeze")
    PointFreezeResponse freeze(@Valid @RequestBody PointFreezeRequest request);

    /**
     * Unfreeze points for a user
     *
     * @param request point freeze request (identifies the freeze to unfreeze)
     * @return point freeze response
     */
    @PostMapping("/api/v1/point/accounts/unfreeze")
    PointFreezeResponse unfreeze(@Valid @RequestBody PointFreezeRequest request);

    /**
     * Deduct points from a user
     *
     * @param request point deduct request
     * @return point account response
     */
    @PostMapping("/api/v1/point/accounts/deduct")
    PointAccountResponse deduct(@Valid @RequestBody PointDeductRequest request);

    /**
     * Refund points to a user
     *
     * @param request point refund request
     * @return point account response
     */
    @PostMapping("/api/v1/point/accounts/refund")
    PointAccountResponse refund(@Valid @RequestBody PointRefundRequest request);

    /**
     * Get point account by user ID
     *
     * @param userId user ID
     * @return point account response
     */
    @GetMapping("/api/v1/point/accounts/{userId}")
    PointAccountResponse getAccount(@PathVariable("userId") Long userId);
}
