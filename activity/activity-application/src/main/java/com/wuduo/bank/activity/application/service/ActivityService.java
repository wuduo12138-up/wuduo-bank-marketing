package com.wuduo.bank.activity.application.service;

import com.wuduo.bank.activity.api.dto.ActivityCreateRequest;
import com.wuduo.bank.activity.api.dto.ActivityResponse;
import com.wuduo.bank.activity.api.dto.ActivityUpdateRequest;

import java.util.List;

/**
 * Activity application service interface
 */
public interface ActivityService {

    /**
     * Create a new activity
     */
    ActivityResponse create(ActivityCreateRequest request);

    /**
     * Update an existing activity
     */
    ActivityResponse update(ActivityUpdateRequest request);

    /**
     * Get activity by ID
     */
    ActivityResponse getById(Long id);

    /**
     * Paginated query of activities
     */
    List<ActivityResponse> page(Integer pageNum, Integer pageSize, Integer status, Integer type);

    /**
     * Audit activity (approve or reject)
     */
    ActivityResponse audit(Long id, Boolean approved);

    /**
     * Get the currently online version of an activity.
     */
    ActivityResponse online(Long id);

    /**
     * Paginated query of activities pending audit.
     */
    List<ActivityResponse> pendingAudit(Integer pageNum, Integer pageSize);

    /**
     * Participate in an activity
     */
    ActivityResponse participate(Long activityId, Long userId);
}
