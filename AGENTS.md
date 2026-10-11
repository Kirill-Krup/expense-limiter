# AGENTS.md

Spring Boot 3.5 / Java 21 microservice: accepts expense transactions, converts them to USD with cached daily
exchange rates and flags those exceeding the monthly limit. See README.md for the full description.

## Commands
- Build + all tests: `mvn -B verify` (unit + integration + e2e; **Docker must be running**, tests use Testcontainers)
- Single test: `mvn -B -q test -Dtest=LimitFlagsIntegrationTest`
- Run locally: `docker compose up -d db` then `mvn spring-boot:run -Dspring-boot.run.profiles=dev`
- Everything in Docker: `docker compose up --build`

## Architecture (package `com.bank.expenselimiter`)
- `web` - controllers, request/response records, MapStruct mappers, `GlobalExceptionHandler` (ProblemDetail). No logic.
- `service` - `TransactionIngestionService` (rate lookup, no transaction) -> `TransactionRegistrar` (short locked
  transaction deciding `limit_exceeded`), `LimitService`, `ExchangeRateService`, `PendingTransactionProcessor`.
- `client` - `ExchangeRateProvider` port, `TwelveDataClient` adapter (timeouts, retries, metrics).
- `domain` - entities + pure rules (`LimitEvaluator`, `MonthWindow`, `UsdConverter`).
- `repository` - Spring Data; native SQL for the effective-limit JOIN with MAX subquery.

## Conventions
- "Now" only through the injected `java.time.Clock`. Money is `BigDecimal` scale 2, `HALF_UP`.
- All settings in `AppProperties` / `application*.yml`; secrets via env vars only.
- Limits are append-only and immutable; there is no update endpoint.
- Never call the external rates API inside a DB transaction or while holding the account lock.
- JSON is snake_case; errors are RFC 9457 `ProblemDetail`.
- Schema changes only through new Flyway migrations (`.agents/skills/create-db-migration`).

## Skills
`.agents/skills/` (symlinked as `.claude/skills`): add-exchange-rate-provider, write-limit-test, create-db-migration.
MCP servers: `.mcp.json` (Claude Code), `.vscode/mcp.json` (Copilot/VS Code).
