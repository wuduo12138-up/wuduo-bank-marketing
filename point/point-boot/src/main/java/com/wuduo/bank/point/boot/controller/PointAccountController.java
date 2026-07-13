package com.wuduo.bank.point.boot.controller;

import com.wuduo.bank.point.api.dto.*;
import com.wuduo.bank.point.application.service.PointAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Point account REST controller
 */
@RestController
@RequestMapping("/api/v1/point/accounts")
@RequiredArgsConstructor
public class PointAccountController {

    private final PointAccountService pointAccountService;

    /**
     * Issue points to a user
     */
    @PostMapping("/issue")
    public PointAccountResponse issue(@Valid @RequestBody PointIssueRequest request) {
        return pointAccountService.issue(request);
    }

    /**
     * Freeze points for a user
     */
    @PostMapping("/freeze")
    public PointFreezeResponse freeze(@Valid @RequestBody PointFreezeRequest request) {
        return pointAccountService.freeze(request);
    }

    /**
     * Unfreeze points for a user
     */
    @PostMapping("/unfreeze")
    public PointFreezeResponse unfreeze(@Valid @RequestBody PointFreezeRequest request) {
        return pointAccountService.unfreeze(request);
    }

    /**
     * Deduct points from a user
     */
    @PostMapping("/deduct")
    public PointAccountResponse deduct(@Valid @RequestBody PointDeductRequest request) {
        return pointAccountService.deduct(request);
    }

    /**
     * Refund points to a user
     */
    @PostMapping("/refund")
    public PointAccountResponse refund(@Valid @RequestBody PointRefundRequest request) {
        return pointAccountService.refund(request);
    }

    /**
     * Get point account by user ID
     */
    @GetMapping("/{userId}")
    public PointAccountResponse getAccount(@PathVariable Long userId) {
        return pointAccountService.getByUserId(userId);
    }
}
