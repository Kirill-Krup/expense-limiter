package com.bank.expenselimiter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Entity
@Table(name = "expense_transaction")
public class ExpenseTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_from", nullable = false, updatable = false)
    private String accountFrom;

    @Column(name = "account_to", nullable = false, updatable = false)
    private String accountTo;

    @Column(name = "currency", nullable = false, updatable = false)
    private String currency;

    @Column(name = "amount", nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", nullable = false, updatable = false)
    private ExpenseCategory category;

    @Column(name = "tx_datetime", nullable = false, updatable = false)
    private Instant datetime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "amount_usd")
    private BigDecimal amountUsd;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_rate_id")
    private ExchangeRate exchangeRate;

    /** The limit that was in force at the transaction time; null when the default limit was applied. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_limit_id")
    private SpendingLimit appliedLimit;

    @Column(name = "applied_limit_sum")
    private BigDecimal appliedLimitSum;

    @Column(name = "limit_exceeded", nullable = false)
    private boolean limitExceeded;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExpenseTransaction() {
    }

    /** A freshly received transaction: waits for the rate until {@link #markProcessed} is called. */
    public static ExpenseTransaction received(IncomingTransaction in, Instant now) {
        ExpenseTransaction tx = new ExpenseTransaction();
        tx.accountFrom = in.accountFrom();
        tx.accountTo = in.accountTo();
        tx.currency = in.currency();
        tx.amount = in.amount();
        tx.category = in.category();
        tx.datetime = in.datetime();
        tx.status = TransactionStatus.PENDING_RATE;
        tx.createdAt = now;
        return tx;
    }

    public void markProcessed(BigDecimal amountUsd, ExchangeRate rate, SpendingLimit limit,
                              BigDecimal limitSum, boolean exceeded) {
        this.amountUsd = amountUsd;
        this.exchangeRate = rate;
        this.appliedLimit = limit;
        this.appliedLimitSum = limitSum.setScale(UsdConverter.MONEY_SCALE, RoundingMode.HALF_UP);
        this.limitExceeded = exceeded;
        this.status = TransactionStatus.PROCESSED;
    }

    public Long getId() {
        return id;
    }

    public String getAccountFrom() {
        return accountFrom;
    }

    public String getAccountTo() {
        return accountTo;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public Instant getDatetime() {
        return datetime;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmountUsd() {
        return amountUsd;
    }

    public ExchangeRate getExchangeRate() {
        return exchangeRate;
    }

    public SpendingLimit getAppliedLimit() {
        return appliedLimit;
    }

    public BigDecimal getAppliedLimitSum() {
        return appliedLimitSum;
    }

    public boolean isLimitExceeded() {
        return limitExceeded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
