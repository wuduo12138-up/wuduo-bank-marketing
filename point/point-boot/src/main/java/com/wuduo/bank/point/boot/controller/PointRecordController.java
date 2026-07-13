package com.wuduo.bank.point.boot.controller;

import com.wuduo.bank.common.model.PageResult;
import com.wuduo.bank.point.api.dto.PointRecordResponse;
import com.wuduo.bank.point.application.service.PointRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Point record REST controller
 */
@RestController
@RequestMapping("/api/v1/point/records")
@RequiredArgsConstructor
public class PointRecordController {

    private final PointRecordService pointRecordService;

    /**
     * Page query point records by user ID
     *
     * @param userId   user ID
     * @param page     page number (1-based), default 1
     * @param pageSize page size, default 10
     * @return paginated point record responses
     */
    @GetMapping("/{userId}")
    public PageResult<PointRecordResponse> pageByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return pointRecordService.pageByUserId(userId, page, pageSize);
    }
}
