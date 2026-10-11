package com.bank.expenselimiter.service;

import com.bank.expenselimiter.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;

/** Maps instants to calendar days in the single configured business zone (app.business-zone). */
@Component
public class BusinessCalendar {

    private final AppProperties properties;

    public BusinessCalendar(AppProperties properties) {
        this.properties = properties;
    }

    public LocalDate dayOf(Instant moment) {
        return moment.atZone(properties.businessZone()).toLocalDate();
    }
}
