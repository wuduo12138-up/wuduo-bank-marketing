package com.wuduo.bank.activity.boot.controller;

import com.wuduo.bank.activity.api.dto.EventReportRequest;
import com.wuduo.bank.activity.api.dto.EventReportResponse;
import com.wuduo.bank.activity.application.service.EventService;
import com.wuduo.bank.common.model.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Event report REST controller.
 * External systems call this endpoint to report business events.
 */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    /**
     * Report a business event for activity processing.
     */
    @PostMapping("/events")
    public R<EventReportResponse> reportEvent(@Valid @RequestBody EventReportRequest request) {
        EventReportResponse response = eventService.processEvent(request);
        return R.ok(response);
    }
}
