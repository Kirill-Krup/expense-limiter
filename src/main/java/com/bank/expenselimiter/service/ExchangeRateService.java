package com.bank.expenselimiter.service;

import com.bank.expenselimiter.client.ExchangeRateProvider;
import com.bank.expenselimiter.exception.ExchangeRateUnavailableException;
import com.bank.expenselimiter.client.RateQuote;
import com.bank.expenselimiter.domain.ExchangeRate;
import com.bank.expenselimiter.repository.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Database first, provider second. A rate is paid for once per (currency, day); parallel requests for the same
 * key share one provider call (single-flight). Deliberately NOT transactional: no DB transaction or lock is held
 * while we wait for the slow external API.
 */
@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);

    private final ExchangeRateRepository rates;
    private final ExchangeRateProvider provider;
    private final ConcurrentMap<String, CompletableFuture<Optional<ExchangeRate>>> inFlight = new ConcurrentHashMap<>();

    public ExchangeRateService(ExchangeRateRepository rates, ExchangeRateProvider provider) {
        this.rates = rates;
        this.provider = provider;
    }

    /** @return the rate, or empty when it is neither cached nor obtainable right now */
    public Optional<ExchangeRate> resolve(String currency, LocalDate date) {
        Optional<ExchangeRate> cached = rates.findByCurrencyAndRateDate(currency, date);
        if (cached.isPresent()) {
            return cached;
        }
        String key = currency + ":" + date;
        CompletableFuture<Optional<ExchangeRate>> mine = new CompletableFuture<>();
        CompletableFuture<Optional<ExchangeRate>> running = inFlight.putIfAbsent(key, mine);
        if (running != null) {
            return running.join();
        }
        try {
            Optional<ExchangeRate> result = fetchAndStore(currency, date);
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private Optional<ExchangeRate> fetchAndStore(String currency, LocalDate date) {
        try {
            RateQuote quote = provider.fetchDailyClose(currency, date);
            rates.insertIfAbsent(currency, date, quote.rate(), quote.sourceDate(), provider.name());
            return rates.findByCurrencyAndRateDate(currency, date);
        } catch (ExchangeRateUnavailableException e) {
            log.warn("No exchange rate for {}/USD on {}: {}", currency, date, e.getMessage());
            return Optional.empty();
        }
    }
}
