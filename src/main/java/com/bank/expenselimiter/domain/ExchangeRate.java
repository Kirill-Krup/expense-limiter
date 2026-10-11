package com.bank.expenselimiter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Cached daily close rate: USD per one unit of {@code currency}. Written only by a native upsert. */
@Entity
@Immutable
@Table(name = "exchange_rate")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(name = "rate", nullable = false)
    private BigDecimal rate;

    @Column(name = "source_date", nullable = false)
    private LocalDate sourceDate;

    @Column(name = "source", nullable = false)
    private String source;

    protected ExchangeRate() {
    }

    public Long getId() {
        return id;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public LocalDate getSourceDate() {
        return sourceDate;
    }

    public String getSource() {
        return source;
    }
}
