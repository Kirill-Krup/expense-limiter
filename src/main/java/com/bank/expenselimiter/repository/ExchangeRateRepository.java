package com.bank.expenselimiter.repository;

import com.bank.expenselimiter.domain.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByCurrencyAndRateDate(String currency, LocalDate rateDate);

    /** Idempotent insert: parallel requests for the same (currency, day) must not fail on the unique key. */
    @Transactional
    @Modifying
    @Query(value = """
            insert into exchange_rate (currency, rate_date, rate, source_date, source)
            values (:currency, :rateDate, :rate, :sourceDate, :source)
            on conflict (currency, rate_date) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("currency") String currency,
                       @Param("rateDate") LocalDate rateDate,
                       @Param("rate") BigDecimal rate,
                       @Param("sourceDate") LocalDate sourceDate,
                       @Param("source") String source);
}
