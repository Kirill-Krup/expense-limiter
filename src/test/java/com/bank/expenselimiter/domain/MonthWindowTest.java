package com.bank.expenselimiter.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class MonthWindowTest {

    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");

    @Test
    void windowCoversTheWholeCalendarMonthInBusinessZone() {
        Instant moment = OffsetDateTime.parse("2022-01-15T12:00:00+06:00").toInstant();

        MonthWindow window = MonthWindow.containing(moment, ALMATY);

        assertThat(window.start()).isEqualTo(OffsetDateTime.parse("2022-01-01T00:00:00+06:00").toInstant());
        assertThat(window.endExclusive()).isEqualTo(OffsetDateTime.parse("2022-02-01T00:00:00+06:00").toInstant());
    }

    @Test
    void lastSecondOfMonthBelongsToThatMonthAndNextMomentToTheNext() {
        Instant lastSecond = OffsetDateTime.parse("2022-01-31T23:59:59+06:00").toInstant();
        Instant firstSecond = OffsetDateTime.parse("2022-02-01T00:00:00+06:00").toInstant();

        MonthWindow january = MonthWindow.containing(lastSecond, ALMATY);

        assertThat(january.endExclusive()).isEqualTo(firstSecond);
        assertThat(MonthWindow.containing(firstSecond, ALMATY).start()).isEqualTo(firstSecond);
    }

    @Test
    void transactionWithForeignOffsetIsAssignedByBusinessZoneNotItsOwnOffset() {
        // 2022-01-31T20:00-05:00 is already 2022-02-01 06:00 in Almaty (+06)
        Instant moment = OffsetDateTime.parse("2022-01-31T20:00:00-05:00").toInstant();

        MonthWindow window = MonthWindow.containing(moment, ALMATY);

        assertThat(window.start()).isEqualTo(OffsetDateTime.parse("2022-02-01T00:00:00+06:00").toInstant());
    }
}
