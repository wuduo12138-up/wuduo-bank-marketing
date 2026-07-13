package com.wuduo.bank.activity.api.client;

import com.wuduo.bank.activity.api.dto.ActivityCreateRequest;
import com.wuduo.bank.activity.api.dto.ActivityResponse;
import com.wuduo.bank.activity.api.dto.ActivityUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Activity Feign client for inter-service communication
 */
@FeignClient(name = "activity", contextId = "activityFeignClient")
public interface ActivityFeignClient {

    @PostMapping("/api/activities")
    ActivityResponse create(@RequestBody ActivityCreateRequest request);

    @PutMapping("/api/activities")
    ActivityResponse update(@RequestBody ActivityUpdateRequest request);

    @GetMapping("/api/activities/{id}")
    ActivityResponse getById(@PathVariable("id") Long id);

    @GetMapping("/api/activities")
    List<ActivityResponse> list();

    @PutMapping("/api/activities/{id}/audit")
    ActivityResponse audit(@PathVariable("id") Long id, @RequestParam("approved") Boolean approved);
}
