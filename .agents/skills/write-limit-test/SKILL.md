---
name: write-limit-test
description: Write a test for limit_exceeded logic in expense-limiter (monthly limit, limit change inside a month, month rollover, remainder exactly 0, parallel transactions, pending exchange rate). Use when asked to add or extend tests of limits, flags or transaction ingestion.
---

# Test the limit logic

The rule: `limit_exceeded = (month spending incl. this transaction) > limit in force at the transaction time`
(`domain/LimitEvaluator`, month = calendar month in `app.business-zone`, default `Asia/Almaty`).
Spending counts only `PROCESSED` transactions of the same `account_from` + `expense_category`.

## Pick the level
- **Pure rule / month boundaries / rounding** -> plain unit test next to `LimitEvaluatorTest`, `MonthWindowTest`,
  `UsdConverterTest` (JUnit 5 + AssertJ, `@ParameterizedTest` with `@CsvSource`). No Spring, no Docker.
- **Anything that touches the DB, limits by date, locking** -> extend `service/LimitFlagsIntegrationTest`
  (it extends `support/AbstractIntegrationTest`: PostgreSQL in Testcontainers via `@ServiceConnection`, WireMock,
  tables truncated before each test).
- **Through HTTP** -> `e2e/Case1EndToEndTest` style (`@LocalServerPort` + `RestClient`).

## Conventions
- Time: never `Instant.now()`. Move the injected `MutableClock` (`clock.set(...)`) before calling
  `LimitService.setLimit(...)` - the limit date is always the clock's "now". Transaction datetime is passed explicitly
  as `OffsetDateTime.parse("2022-01-13T10:00:00+06:00").toInstant()`.
- Use `USD` transactions to test pure limit behaviour (no rate needed). For other currencies stub the provider with
  `RateStubs.close(WIREMOCK, "KZT", "0.01")` or `RateStubs.serverError(WIREMOCK)`.
- Accounts are 10 digits (`"0000000123"`); categories `ExpenseCategory.PRODUCT` / `SERVICE`.
- Call `TransactionIngestionService.accept(new IncomingTransaction(...))`; assert `isLimitExceeded()` and, for
  pending rates, `getStatus()`; call `PendingTransactionProcessor.processPending()` to simulate the retry job.
- Concurrency tests: `Executors.newVirtualThreadPerTaskExecutor()` + `CountDownLatch` so all tasks start together.
- Name tests by behaviour (`remainingExactlyZeroIsNotExceededButOneCentOverIs`), one scenario per test.

## Run
`mvn -B -q test -Dtest=LimitFlagsIntegrationTest` (Docker must be running), full check: `mvn -B verify`.
