package com.bank.expenselimiter.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

/**
 * All tunable settings of the service (prefix {@code app}). Nothing is hard-coded in the services.
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotNull ZoneId businessZone,
        @NotNull @Positive BigDecimal defaultLimitUsd,
        @Valid @NotNull TwelveData twelvedata,
        @Valid @NotNull Pending pending) {

    public record TwelveData(
            @NotBlank String baseUrl,
            @NotBlank String apiKey,
            @NotNull Duration connectTimeout,
            @NotNull Duration readTimeout,
            @Min(1) int maxAttempts,
            @NotNull Duration retryBackoff) {
    }

    public record Pending(
            @NotNull Duration retryInterval,
            @Min(1) int batchSize) {
    }
}
