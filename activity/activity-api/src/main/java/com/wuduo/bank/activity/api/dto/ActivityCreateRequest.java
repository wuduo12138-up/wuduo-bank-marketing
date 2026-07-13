package com.wuduo.bank.activity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Activity creation request DTO
 */
@Data
public class ActivityCreateRequest {

    @NotBlank(message = "Activity title cannot be blank")
    private String title;

    @NotNull(message = "Activity type cannot be null")
    private Integer type;

    @NotNull(message = "Start time cannot be null")
    private LocalDateTime startTime;

    @NotNull(message = "End time cannot be null")
    private LocalDateTime endTime;

    @NotNull(message = "Budget amount cannot be null")
    private BigDecimal budgetAmount;

    private String ruleConfig;

    /**
     * Whether to submit for audit immediately (true = PENDING_AUDIT, false/null = DRAFT)
     */
    private Boolean submit;
}
