package com.bank.expenselimiter.service;

import com.bank.expenselimiter.domain.ExpenseCategory;
import com.bank.expenselimiter.domain.ExpenseTransaction;
import com.bank.expenselimiter.domain.IncomingTransaction;
import com.bank.expenselimiter.domain.TransactionStatus;
import com.bank.expenselimiter.repository.ExpenseTransactionRepository;
import com.bank.expenselimiter.support.AbstractIntegrationTest;
import com.bank.expenselimiter.support.RateStubs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class LimitFlagsIntegrationTest extends AbstractIntegrationTest {

    private static final String ACCOUNT = "0000000123";
    private static final ExpenseCategory PRODUCT = ExpenseCategory.PRODUCT;

    @Autowired
    TransactionIngestionService ingestion;
    @Autowired
    LimitService limitService;
    @Autowired
    PendingTransactionProcessor pendingProcessor;
    @Autowired
    ExpenseTransactionRepository transactions;

    @Test
    void case1_limitChangedInsideMonthDoesNotAffectEarlierTransactions() {
        clock.set(at("2022-01-01T00:00:00+06:00"));
        limitService.setLimit(ACCOUNT, PRODUCT, new BigDecimal("1000"));

        assertThat(flag("500", "2022-01-02T10:00:00+06:00")).isFalse();
        assertThat(flag("600", "2022-01-03T10:00:00+06:00")).isTrue();

        clock.set(at("2022-01-10T00:00:00+06:00"));
        limitService.setLimit(ACCOUNT, PRODUCT, new BigDecimal("2000"));

        assertThat(flag("100", "2022-01-11T10:00:00+06:00")).isFalse();
        assertThat(flag("700", "2022-01-12T10:00:00+06:00")).isFalse();
        assertThat(flag("100", "2022-01-13T10:00:00+06:00")).isFalse(); // remainder is exactly 0
        assertThat(flag("100", "2022-01-13T11:00:00+06:00")).isTrue();
    }

    @Test
    void case2_newLimitBelowAlreadySpentAmountFlagsEverythingAfterIt() {
        clock.set(at("2022-02-01T00:00:00+06:00"));
        limitService.setLimit(ACCOUNT, PRODUCT, new BigDecimal("1000"));
        assertThat(flag("500", "2022-02-02T10:00:00+06:00")).isFalse();
        assertThat(flag("100", "2022-02-03T10:00:00+06:00")).isFalse();

        clock.set(at("2022-02-10T00:00:00+06:00"));
        limitService.setLimit(ACCOUNT, PRODUCT, new BigDecimal("400"));

        assertThat(flag("100", "2022-02-11T10:00:00+06:00")).isTrue();
        assertThat(flag("100", "2022-02-12T10:00:00+06:00")).isTrue();
    }

    @Test
    void newMonthStartsFromZeroSpending() {
        assertThat(flag("900", "2022-03-31T23:59:00+06:00")).isFalse();
        assertThat(flag("900", "2022-04-01T00:00:00+06:00")).isFalse(); // default limit 1000, fresh month
        assertThat(flag("200", "2022-04-02T00:00:00+06:00")).isTrue();
    }

    @Test
    void remainingExactlyZeroIsNotExceededButOneCentOverIs() {
        assertThat(flag("600", "2022-05-02T10:00:00+06:00")).isFalse();
        assertThat(flag("400", "2022-05-03T10:00:00+06:00")).isFalse(); // exactly 1000 -> remainder 0
        assertThat(flag("0.01", "2022-05-04T10:00:00+06:00")).isTrue();
    }

    @Test
    void categoriesAndAccountsHaveIndependentBudgets() {
        assertThat(flag("1000", "2022-06-02T10:00:00+06:00")).isFalse();
        ExpenseTransaction service = ingestion.accept(new IncomingTransaction(
                ACCOUNT, "9999999999", "USD", new BigDecimal("1000"), ExpenseCategory.SERVICE,
                at("2022-06-03T10:00:00+06:00")));
        ExpenseTransaction otherAccount = ingestion.accept(new IncomingTransaction(
                "0000000456", "9999999999", "USD", new BigDecimal("1000"), PRODUCT,
                at("2022-06-03T10:00:00+06:00")));

        assertThat(service.isLimitExceeded()).isFalse();
        assertThat(otherAccount.isLimitExceeded()).isFalse();
    }

    @Test
    void parallelTransactionsNeverSeeTheSameRemainingLimit() throws Exception {
        int parallel = 10; // 10 x 200 USD against the default 1000 USD limit
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < parallel; i++) {
            Callable<Boolean> task = () -> {
                start.await();
                return flag("200", "2022-07-05T10:00:00+06:00");
            };
            results.add(pool.submit(task));
        }
        start.countDown();

        long exceeded = 0;
        for (Future<Boolean> result : results) {
            exceeded += result.get() ? 1 : 0;
        }
        pool.shutdown();

        // 5 x 200 fit exactly into 1000, the other 5 must be flagged: no lost updates
        assertThat(exceeded).isEqualTo(5);
    }

    @Test
    void rateUnavailable_transactionIsStoredAsPendingAndCompletedLater() {
        RateStubs.serverError(WIREMOCK);
        ExpenseTransaction stored = ingestion.accept(new IncomingTransaction(
                ACCOUNT, "9999999999", "KZT", new BigDecimal("50000.00"), PRODUCT, at("2022-08-02T10:00:00+06:00")));

        assertThat(stored.getStatus()).isEqualTo(TransactionStatus.PENDING_RATE);
        assertThat(stored.getAmountUsd()).isNull();
        assertThat(stored.isLimitExceeded()).isFalse();

        RateStubs.close(WIREMOCK, "KZT", "0.01");
        int completed = pendingProcessor.processPending();

        assertThat(completed).isEqualTo(1);
        ExpenseTransaction reloaded = transactions.findById(stored.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(reloaded.getAmountUsd()).isEqualByComparingTo("500.00");
    }

    private boolean flag(String usd, String datetime) {
        return ingestion.accept(new IncomingTransaction(
                ACCOUNT, "9999999999", "USD", new BigDecimal(usd), PRODUCT, at(datetime))).isLimitExceeded();
    }

    private static Instant at(String isoWithOffset) {
        return OffsetDateTime.parse(isoWithOffset).toInstant();
    }
}
