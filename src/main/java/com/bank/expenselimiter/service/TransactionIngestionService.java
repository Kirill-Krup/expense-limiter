package com.bank.expenselimiter.service;

import com.bank.expenselimiter.domain.ExchangeRate;
import com.bank.expenselimiter.domain.ExpenseTransaction;
import com.bank.expenselimiter.domain.IncomingTransaction;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Entry point for incoming transactions. Order matters: the (possibly slow) rate lookup happens first,
 * outside of any transaction/lock; only then the short locked section in {@link TransactionRegistrar} runs.
 */
@Service
public class TransactionIngestionService {

    private final ExchangeRateService exchangeRates;
    private final TransactionRegistrar registrar;
    private final BusinessCalendar calendar;

    public TransactionIngestionService(ExchangeRateService exchangeRates, TransactionRegistrar registrar,
                                       BusinessCalendar calendar) {
        this.exchangeRates = exchangeRates;
        this.registrar = registrar;
        this.calendar = calendar;
    }

    public ExpenseTransaction accept(IncomingTransaction incoming) {
        CurrencyCodes.requireKnown(incoming.currency());
        Optional<ExchangeRate> rate = CurrencyCodes.isUsd(incoming.currency())
                ? Optional.empty()
                : exchangeRates.resolve(incoming.currency(), calendar.dayOf(incoming.datetime()));
        return registrar.register(incoming, rate);
    }
}
