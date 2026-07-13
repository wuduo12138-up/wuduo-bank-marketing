package com.wuduo.bank.activity.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;

/**
 * Frequency period enum — determines the time window for rate limiting.
 * Each value knows how to generate a period key for the current time.
 */
@Getter
@AllArgsConstructor
public enum FrequencyPeriod {

    DAILY("Daily") {
        @Override
        public String generatePeriodKey() {
            return LocalDate.now().toString();
        }
    },
    WEEKLY("Weekly") {
        @Override
        public String generatePeriodKey() {
            LocalDate now = LocalDate.now();
            LocalDate monday = now.with(java.time.DayOfWeek.MONDAY);
            return monday.toString();
        }
    },
    MONTHLY("Monthly") {
        @Override
        public String generatePeriodKey() {
            return YearMonth.now().toString();
        }
    },
    YEARLY("Yearly") {
        @Override
        public String generatePeriodKey() {
            return String.valueOf(Year.now().getValue());
        }
    },
    LIFETIME("Lifetime") {
        @Override
        public String generatePeriodKey() {
            return "LIFETIME";
        }
    };

    private final String description;

    /**
     * Generate a unique period key for the current time.
     */
    public abstract String generatePeriodKey();
}
