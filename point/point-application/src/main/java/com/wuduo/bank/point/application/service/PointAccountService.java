package com.wuduo.bank.point.application.service;

import com.wuduo.bank.point.api.dto.*;

/**
 * Point account application service
 */
public interface PointAccountService {

    /**
     * Issue points to a user
     *
     * @param request point issue request
     * @return point account response
     */
    PointAccountResponse issue(PointIssueRequest request);

    /**
     * Freeze points for a user
     *
     * @param request point freeze request
     * @return point freeze response
     */
    PointFreezeResponse freeze(PointFreezeRequest request);

    /**
     * Unfreeze points for a user
     *
     * @param request point freeze request
     * @return point freeze response
     */
    PointFreezeResponse unfreeze(PointFreezeRequest request);

    /**
     * Deduct points from a user
     *
     * @param request point deduct request
     * @return point account response
     */
    PointAccountResponse deduct(PointDeductRequest request);

    /**
     * Refund points to a user
     *
     * @param request point refund request
     * @return point account response
     */
    PointAccountResponse refund(PointRefundRequest request);

    /**
     * Get point account by user ID
     *
     * @param userId user ID
     * @return point account response
     */
    PointAccountResponse getByUserId(Long userId);
}
