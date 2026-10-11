package com.bank.expenselimiter.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Half-open interval [start, endExclusive) of the calendar month that contains a moment in a given zone. */
public record MonthWindow(Instant start, Instant endExclusive) {

    public static MonthWindow containing(Instant moment, ZoneId zone) {
        LocalDate firstDay = moment.atZone(zone).toLocalDate().withDayOfMonth(1);
        return new MonthWindow(
                firstDay.atStartOfDay(zone).toInstant(),
                firstDay.plusMonths(1).atStartOfDay(zone).toInstant());
    }
}
