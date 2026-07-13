package com.wuduo.bank.activity.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wuduo.bank.activity.api.enums.FrequencyPeriod;
import com.wuduo.bank.activity.api.enums.TriggerType;
import lombok.Data;

/**
 * Parsed representation of the activity's {@code ruleConfig} JSON field.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RuleConfig {

    @JsonProperty("triggerType")
    private String triggerType;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("threshold")
    private Long threshold;

    @JsonProperty("frequency")
    private FrequencyConfig frequency;

    @JsonProperty("reward")
    private RewardConfig reward;

    /**
     * Optional. When set on an ACCUMULATION_TRIGGER activity, the value of this
     * field is extracted from {@code eventData} JSON and added to the counter
     * instead of incrementing by 1. Example: {@code "transAmount"} extracts the
     * {@code transAmount} field from {@code {"transAmount":"100.00"}}.
     * <p>
     * When absent (null), each event increments the counter by 1 (event counting).
     */
    @JsonProperty("accumulateField")
    private String accumulateField;

    @JsonProperty("criteriaService")
    private String criteriaService;

    @JsonProperty("criteriaEndpoint")
    private String criteriaEndpoint;

    /**
     * @return the TriggerType enum for the raw triggerType string, or null
     */
    public TriggerType getTriggerTypeEnum() {
        if (triggerType == null) {
            return null;
        }
        try {
            return TriggerType.valueOf(triggerType);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @return the FrequencyPeriod enum for the frequency config, or null
     */
    public FrequencyPeriod getFrequencyPeriodEnum() {
        if (frequency == null || frequency.getPeriod() == null) {
            return null;
        }
        try {
            return FrequencyPeriod.valueOf(frequency.getPeriod());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FrequencyConfig {

        @JsonProperty("period")
        private String period;

        @JsonProperty("maxCount")
        private Integer maxCount;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RewardConfig {

        @JsonProperty("type")
        private String type;

        @JsonProperty("amount")
        private Long amount;
    }
}
