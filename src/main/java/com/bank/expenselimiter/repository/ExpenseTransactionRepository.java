package com.bank.expenselimiter.repository;

import com.bank.expenselimiter.domain.ExpenseCategory;
import com.bank.expenselimiter.domain.ExpenseTransaction;
import com.bank.expenselimiter.domain.TransactionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ExpenseTransactionRepository extends JpaRepository<ExpenseTransaction, Long> {

    /** Month spending in USD (null when there is nothing yet). Only fully processed transactions count. */
    @Query("""
            select sum(t.amountUsd) from ExpenseTransaction t
             where t.accountFrom = :account
               and t.category = :category
               and t.status = :status
               and t.datetime >= :windowStart
               and t.datetime < :windowEnd
            """)
    BigDecimal sumUsd(@Param("account") String account,
                      @Param("category") ExpenseCategory category,
                      @Param("status") TransactionStatus status,
                      @Param("windowStart") Instant windowStart,
                      @Param("windowEnd") Instant windowEnd);

    @Query("""
            select t from ExpenseTransaction t
              left join fetch t.appliedLimit
             where t.accountFrom = :account
               and t.limitExceeded = true
             order by t.datetime, t.id
            """)
    List<ExpenseTransaction> findExceeded(@Param("account") String account);

    List<ExpenseTransaction> findByStatusOrderByIdAsc(TransactionStatus status, Pageable pageable);
}
