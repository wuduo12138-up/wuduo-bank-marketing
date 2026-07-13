package com.wuduo.bank.activity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Event report request — sent by external business systems to report
 * a business event that may trigger one or more activity completions.
 */
@Data
public class EventReportRequest {

    /**
     * Event type identifier — matched against activity ruleConfig.eventType
     */
    @NotBlank(message = "Event type cannot be blank")
    private String eventType;

    /**
     * User who performed the business action
     */
    @NotNull(message = "User ID cannot be null")
    private Long userId;

    /**
     * Business number for idempotency — same bizNo never triggers duplicate rewards
     */
    @NotBlank(message = "Business number cannot be blank")
    private String bizNo;

    /**
     * Optional JSON payload for extensibility (e.g. event-specific metadata)
     */
    private String eventData;
}
