package com.wuduo.bank.activity.api.dto;

import lombok.Data;

/**
 * Event report response — summarizes what the event triggered.
 */
@Data
public class EventReportResponse {

    /**
     * The activity that was matched and processed
     */
    private Long activityId;

    /**
     * Number of new completions awarded by this event
     */
    private Integer completionsAwarded;

    /**
     * Total reward amount issued by this event
     */
    private Long rewardAmount;

    /**
     * Whether this event was skipped (duplicate or no matching activity)
     */
    private Boolean skipped;

    /**
     * Optional message explaining the result
     */
    private String message;
}
