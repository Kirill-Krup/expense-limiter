package com.bank.expenselimiter.web.mapper;

import com.bank.expenselimiter.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;

/** Presents stored instants in the business zone, e.g. 2022-01-10T00:00:00+06:00. */
@Component
public class TimeMapper {

    private final AppProperties properties;

    public TimeMapper(AppProperties properties) {
        this.properties = properties;
    }

    public OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atZone(properties.businessZone()).toOffsetDateTime();
    }
}
