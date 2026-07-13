package com.wuduo.bank.activity.application.service;

import com.wuduo.bank.activity.api.dto.EventReportRequest;
import com.wuduo.bank.activity.api.dto.EventReportResponse;

/**
 * Event processing service — receives external business events,
 * matches them to activities, and dispatches to trigger strategies.
 */
public interface EventService {

    /**
     * Process an external business event.
     *
     * @param request the event report
     * @return processing result summary
     */
    EventReportResponse processEvent(EventReportRequest request);
}
