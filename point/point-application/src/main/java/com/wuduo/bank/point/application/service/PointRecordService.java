package com.wuduo.bank.point.application.service;

import com.wuduo.bank.common.model.PageResult;
import com.wuduo.bank.point.api.dto.PointRecordResponse;

/**
 * Point record application service
 */
public interface PointRecordService {

    /**
     * Page query point records by user ID
     *
     * @param userId   user ID
     * @param page     page number (1-based)
     * @param pageSize page size
     * @return paginated point record responses
     */
    PageResult<PointRecordResponse> pageByUserId(Long userId, Integer page, Integer pageSize);
}
