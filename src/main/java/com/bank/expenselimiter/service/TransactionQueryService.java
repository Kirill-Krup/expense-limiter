package com.bank.expenselimiter.service;

import com.bank.expenselimiter.domain.ExpenseTransaction;
import com.bank.expenselimiter.repository.ExpenseTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionQueryService {

    private final ExpenseTransactionRepository transactions;

    public TransactionQueryService(ExpenseTransactionRepository transactions) {
        this.transactions = transactions;
    }

    /** Transactions flagged limit_exceeded, each with the limit that was in force (fetched in the same query). */
    @Transactional(readOnly = true)
    public List<ExpenseTransaction> findExceeded(String account) {
        return transactions.findExceeded(account);
    }
}
