package com.bank.expenselimiter.repository;

import com.bank.expenselimiter.domain.ExpenseCategory;
import com.bank.expenselimiter.domain.SpendingLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SpendingLimitRepository extends JpaRepository<SpendingLimit, Long> {

    List<SpendingLimit> findByAccountOrderBySetAtDescIdDesc(String account);

    boolean existsByAccountAndCategoryAndSetAt(String account, ExpenseCategory category, Instant setAt);

    /**
     * The limit in force at {@code moment}: the latest one whose set_at is not later than the moment.
     * JOIN with an aggregating subquery (MAX) as required by the assignment;
     * uniqueness of (account, category, set_at) guarantees at most one row.
     */
    @Query(value = """
            select l.*
              from spending_limit l
              join (select max(set_at) as max_set_at
                      from spending_limit
                     where account = :account
                       and expense_category = :category
                       and set_at <= :moment) latest
                on l.set_at = latest.max_set_at
             where l.account = :account
               and l.expense_category = :category
            """, nativeQuery = true)
    Optional<SpendingLimit> findEffective(@Param("account") String account,
                                          @Param("category") String category,
                                          @Param("moment") Instant moment);
}
