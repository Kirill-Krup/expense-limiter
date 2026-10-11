---
name: add-exchange-rate-provider
description: Add a new external exchange-rate source (e.g. alphavantage.co, openexchangerates.org) to the expense-limiter service by implementing the ExchangeRateProvider port. Use when asked to integrate, replace or add a rates provider/API client, or to change how daily close rates are fetched.
---

# Add an exchange-rate provider

The service never talks to a rates API directly: `ExchangeRateService` (package `com.bank.expenselimiter.service`)
depends on the port `com.bank.expenselimiter.client.ExchangeRateProvider`. The only implementation today is
`TwelveDataClient`. Follow these steps; do not touch `ExchangeRateService` or `TransactionRegistrar`.

## Contract to honour
`RateQuote fetchDailyClose(String currency, LocalDate date)` returns **USD per 1 unit of `currency`**
(`KZT` -> ~0.0022). It must return the close of `date`, or the last close before it when `date` is a weekend/holiday
(put the real trading day into `RateQuote.sourceDate`). When no rate can be obtained throw
`ExchangeRateUnavailableException` - never return null, never throw anything else to the caller.

## Steps
1. Create `src/main/java/com/bank/expenselimiter/client/<Name>Client.java` implementing `ExchangeRateProvider`;
   `name()` returns a short lowercase id stored in `exchange_rate.source`.
2. Annotate it `@Component` and `@ConditionalOnProperty(name = "app.rate-provider", havingValue = "<id>")`
   (only one provider bean may be active; `TwelveDataClient` is `matchIfMissing = true` - remove that attribute from it
   if the new provider becomes the default and set `app.rate-provider` in `application.yml`).
3. Settings go to `AppProperties` (record, `@Validated`) + `application.yml`; secrets only via env vars
   (`${NAME_API_KEY:}`), never in the repo.
4. Build the `RestClient` exactly like `TwelveDataClient` does: explicit connect/read timeouts
   (`JdkClientHttpRequestFactory`), retry only transient failures (I/O, 5xx, 429), no retry for 4xx/error payloads,
   Micrometer timer `exchange_rate.client.requests` tagged `provider` and `outcome`.
5. Response records need `@JsonIgnoreProperties(ignoreUnknown = true)` (the app enables FAIL_ON_UNKNOWN_PROPERTIES).
6. Write `src/test/java/com/bank/expenselimiter/client/<Name>ClientTest.java` modelled on `TwelveDataClientTest`
   (WireMock): success, weekend fallback date, retry on 5xx, timeout, error payload.
7. Run `mvn -B -q test -Dtest=<Name>ClientTest`, then `mvn -B verify` (needs Docker for Testcontainers).
8. Mention the new provider and its env var in README.md ("Настройки").
