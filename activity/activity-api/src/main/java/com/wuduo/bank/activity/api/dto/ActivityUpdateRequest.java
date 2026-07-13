package com.wuduo.bank.activity.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Activity update request DTO
 */
@Data
public class ActivityUpdateRequest {

    @NotNull(message = "Activity ID cannot be null")
    private Long id;

    private String title;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal budgetAmount;

    private String ruleConfig;

    /**
     * Whether to submit for audit after saving (null / false = save as draft only)
     */
    private Boolean submit;
}
