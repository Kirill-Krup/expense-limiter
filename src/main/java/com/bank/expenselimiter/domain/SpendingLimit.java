package com.bank.expenselimiter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/** A monthly limit in USD. Immutable: limits are never updated, a new one is appended instead. */
@Entity
@Immutable
@Table(name = "spending_limit")
public class SpendingLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account", nullable = false)
    private String account;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", nullable = false)
    private ExpenseCategory category;

    @Column(name = "limit_sum", nullable = false)
    private BigDecimal limitSum;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    protected SpendingLimit() {
    }

    public static SpendingLimit create(String account, ExpenseCategory category, BigDecimal limitSum, Instant setAt) {
        SpendingLimit limit = new SpendingLimit();
        limit.account = account;
        limit.category = category;
        limit.limitSum = limitSum;
        limit.setAt = setAt;
        return limit;
    }

    public Long getId() {
        return id;
    }

    public String getAccount() {
        return account;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public BigDecimal getLimitSum() {
        return limitSum;
    }

    public Instant getSetAt() {
        return setAt;
    }
}
