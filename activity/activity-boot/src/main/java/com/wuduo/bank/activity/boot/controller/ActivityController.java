package com.wuduo.bank.activity.boot.controller;

import com.wuduo.bank.activity.api.dto.ActivityCreateRequest;
import com.wuduo.bank.activity.api.dto.ActivityResponse;
import com.wuduo.bank.activity.api.dto.ActivityUpdateRequest;
import com.wuduo.bank.activity.application.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Activity REST controller
 */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * Create a new activity
     */
    @PostMapping
    public ActivityResponse create(@Valid @RequestBody ActivityCreateRequest request) {
        return activityService.create(request);
    }

    /**
     * Update an existing activity
     */
    @PutMapping
    public ActivityResponse update(@Valid @RequestBody ActivityUpdateRequest request) {
        return activityService.update(request);
    }

    /**
     * Get activity by ID
     */
    @GetMapping("/{id}")
    public ActivityResponse getById(@PathVariable Long id) {
        return activityService.getById(id);
    }

    /**
     * Paginated query of activities
     */
    @GetMapping
    public List<ActivityResponse> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer type) {
        return activityService.page(pageNum, pageSize, status, type);
    }

    /**
     * Audit activity
     */
    @PutMapping("/{id}/audit")
    public ActivityResponse audit(@PathVariable Long id, @RequestParam Boolean approved) {
        return activityService.audit(id, approved);
    }

    /**
     * Activities pending audit (any version with audit_status=1)
     */
    @GetMapping("/pending-audit")
    public List<ActivityResponse> pendingAudit(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return activityService.pendingAudit(pageNum, pageSize);
    }

    /**
     * Get the currently online version of an activity
     */
    @GetMapping("/{id}/online")
    public ActivityResponse online(@PathVariable Long id) {
        return activityService.online(id);
    }

    /**
     * Participate in an activity
     */
    @PostMapping("/{id}/participate")
    public ActivityResponse participate(@PathVariable Long id, @RequestParam Long userId) {
        return activityService.participate(id, userId);
    }
}
