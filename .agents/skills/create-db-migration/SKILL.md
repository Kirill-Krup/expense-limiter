---
name: create-db-migration
description: Create a Flyway database migration for expense-limiter (new table, column, index or constraint in PostgreSQL) and keep JPA entities in sync. Use when the task changes the database schema.
---

# Create a Flyway migration

Schema is owned by Flyway (schema first); Hibernate runs with `ddl-auto: none`, so a forgotten migration shows up as a
runtime SQL error in the integration tests, not at startup.

## Rules
1. Location: `src/main/resources/db/migration/`. Name: `V<next integer>__<snake_case_description>.sql`
   (look at existing files first; current baseline is `V1__init_schema.sql`). Two underscores after the version.
2. **Never edit an already committed migration** - add a new one.
3. Tables/columns are `snake_case`; money is `numeric(19,2)`; instants are `timestamptz`; rates `numeric(19,10)`;
   enums are `varchar` holding the Java enum name (entity uses `@Enumerated(EnumType.STRING)`).
4. Name constraints and indexes explicitly (`uq_<table>`, `ix_<table>_<purpose>`); add partial indexes only for the
   hot queries (see `ix_tx_month_spend`, `ix_tx_exceeded`, `ix_tx_pending`).
5. `spending_limit` rows are append-only; do not add UPDATE paths for limits.
6. Update the matching entity in `com.bank.expenselimiter.domain` (`@Column(name = ...)`) and, if the API changes,
   the DTO + MapStruct mapper in `com.bank.expenselimiter.web`.

## Verify
- `mvn -B verify` (the Testcontainers PostgreSQL applies every migration from scratch; Docker must be running).
- Optionally inspect the result through the `postgres` MCP server (read-only) configured in `.mcp.json`.
