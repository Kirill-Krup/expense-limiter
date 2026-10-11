# Expense limiter

Микросервис банковской системы: принимает расходные операции в любых валютах, переводит их в USD по дневному
курсу закрытия, ведёт месячные лимиты по категориям `product` / `service` и помечает операции, превысившие лимит
(`limit_exceeded`).

**Стек:** Java 21, Spring Boot 3.5, Spring Web MVC на виртуальных потоках, Spring Data JPA, PostgreSQL 16, Flyway,
MapStruct, springdoc (OpenAPI), Micrometer/Actuator, JUnit 5 + AssertJ + Testcontainers + WireMock.

## Запуск

Нужны Docker и JDK 21.

```bash

docker compose up -d db
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- Swagger UI: <http://localhost:8080/swagger-ui.html>, OpenAPI: `/v3/api-docs`
- Health: `/actuator/health`, метрики: `/actuator/metrics` (в т.ч. `exchange_rate.client.requests`)
- Тесты: `mvn verify` — единственное условие: запущенный Docker (PostgreSQL поднимает Testcontainers).
  Maven Wrapper добавляется один раз командой `mvn wrapper:wrapper`, после чего работает `./mvnw verify`.

### Настройки (переменные окружения)

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | localhost / expense / expense | PostgreSQL |
| `TWELVEDATA_API_KEY` | `demo` | ключ внешнего API курсов |
| `SPRING_PROFILES_ACTIVE` | — | `dev` (SQL-логи, debug), `prod` (JSON-логи) |

Остальное (таймауты, число попыток, лимит по умолчанию, часовой пояс) — `app.*` в `application.yml`.

Ключи и учётные данные в репозитории не хранятся: в коде и в CI-конфигурации они задаются только через
переменные окружения (локально) и секреты репозитория (GitHub Actions).

## API

Два API без разграничения доступа (по условию прототипа). Ошибки везде в формате `ProblemDetail` (RFC 9457).
JSON — snake_case, как в задании.

**A. Приём транзакций (интеграция с банковскими сервисами)**

`POST /api/v1/integration/transactions` → `201`

```json
{"account_from":"0000000123","account_to":"9999999999","currency_shortname":"KZT",
  "sum":10000.45,"expense_category":"product","datetime":"2022-01-30T00:00:00+06:00"}
```
Ответ содержит сохранённую транзакцию, `limit_exceeded` и `status` (`PROCESSED` | `PENDING_RATE`).

**Б. Клиентский API**

| Метод | Путь | Описание |
|---|---|---|
| `POST` | `/api/v1/client/limits` | установить новый лимит `{account, expense_category, limit_sum}`; дата = «сейчас» на сервере |
| `GET` | `/api/v1/client/limits?account=` | все лимиты счёта (новые сверху) |
| `GET` | `/api/v1/client/transactions/exceeded?account=` | транзакции с превышением + `limit_sum`, `limit_datetime`, `limit_currency_shortname` |

Обновления лимита нет вообще (ни PUT, ни PATCH). Попытка передать `limit_datetime` в теле → `400`
(неизвестные поля запрещены), поэтому выставить дату в прошлом/будущем нельзя.

## Архитектура

```
web  ──►  service  ──►  repository ──► PostgreSQL
 │          │  └──►  client (ExchangeRateProvider ──► TwelveDataClient ──► внешний API)
 └─ DTO/MapStruct   └──►  domain (entities + чистые правила)
```

- `TransactionIngestionService` — сначала получает курс (БД → внешний API), **вне транзакции и вне блокировки**.
- `TransactionRegistrar` — короткая транзакция: берёт блокировку на (счёт, категория), считает расходы месяца,
  находит действующий лимит, выставляет флаг, сохраняет.
- `LimitEvaluator`, `MonthWindow`, `UsdConverter` — чистая логика без Spring, покрыта unit-тестами.

### Логика флага `limit_exceeded`

`limit_exceeded = (расходы месяца в USD, включая эту операцию) > лимит, действовавший на момент операции`.

- Лимит «на момент операции» — последний, у которого `set_at <= datetime` операции. Поэтому новый лимит не влияет
  на более ранние операции. Запрос — JOIN с подзапросом `MAX(set_at)` (`SpendingLimitRepository.findEffective`).
- Расходы месяца считаются целиком (и до, и после смены лимита) — именно так получаются остатки `900` и `-200`
  в таблице задания. Остаток ровно 0 — не превышение.
- Если лимит не задан — `app.default-limit-usd` (1000 USD); в ответе `limit_datetime` тогда `null`.
- Категории и счета считаются независимо.

### Принятые решения

- **Часовой пояс месяца: `Asia/Almaty`** (`app.business-zone`). Границы месяца и «день курса» считаются в нём, независимо
  от смещения во входящем `datetime`. Время в ответах тоже отдаётся в этом поясе.
- **«Клиент» = `account_from`.** Лимиты и расходы ведутся по паре (счёт, категория), поэтому в API лимитов есть поле
  `account`. Если по вашему пониманию лимит общий на банк — замените `account` на константу в `LimitService`/`TransactionRegistrar`.
- **Параллельные транзакции.** Критическая секция сериализуется PostgreSQL advisory-блокировкой
  `pg_advisory_xact_lock(hashtextextended(account:category, 0))` в одной транзакции с чтением суммы и записью:
  два запроса не могут увидеть один и тот же остаток. Блокировка не требует строки в БД (работает для первой
  операции счёта) и снимается автоматически. Проверяется тестом `parallelTransactionsNeverSeeTheSameRemainingLimit`.
- **Курсы.** `GET /time_series?symbol=KZT/USD&interval=1day&end_date=<день>&outputsize=1` возвращает close этого дня
  или, в выходной/праздник, последний предыдущий close (`previous_close` из задания); фактический день сохраняется в
  `exchange_rate.source_date`. Курс хранится в БД (`unique(currency, rate_date)`), на каждую пару (валюта, день)
  платный запрос делается один раз; параллельные запросы одного курса объединяются (single-flight).

### Стратегия при сбоях внешнего API

1. Явные таймауты: connect 2 с, read 3 с.
2. До 3 попыток с линейной паузой — только для временных ошибок (I/O, 5xx, 429). Ошибки 4xx и `status: error`
   в ответе не повторяются.
3. Если курс получить не удалось, транзакция **всё равно сохраняется** со статусом `PENDING_RATE`
   (`limit_exceeded = false`, `amount_usd = null`). Фоновая задача `PendingTransactionProcessor`
   (раз в минуту) досчитывает их, когда курс появится, и тогда выставляет флаг по тем же правилам. Данные не теряются.
4. Метрика `exchange_rate.client.requests{provider,outcome}` показывает долю ошибок.

Ограничение: ожидающие транзакции учитываются в расходах месяца только после досчёта (порядок = порядок досчёта).

## Данные

Миграции: `src/main/resources/db/migration` (Flyway, schema first). Таблицы: `exchange_rate`, `spending_limit`
(append-only, `@Immutable`), `expense_transaction`. Индексы под горячие запросы — частичные (`ix_tx_month_spend`,
`ix_tx_exceeded`, `ix_tx_pending`).

## Тесты

| Тест | Что проверяет |
|---|---|
| `LimitEvaluatorTest`, `MonthWindowTest`, `UsdConverterTest` | правило превышения, остаток 0, границы месяца и часового пояса, округление |
| `TwelveDataClientTest` (WireMock) | разбор ответа, выходной день, ретраи, таймаут, error-payload |
| `LimitFlagsIntegrationTest` (Testcontainers) | случаи 1 и 2 из задания, смена месяца, остаток 0, независимость категорий, **параллельные запросы**, `PENDING_RATE` |
| `Case1EndToEndTest` | «случай 1» целиком через HTTP (реальный порт + PostgreSQL + WireMock), ровно транзакции 3 и 13 января с данными лимита |

## CI

GitHub Actions (push в `main` и pull request): на раннере поднимается PostgreSQL 16 как service container, затем
выполняется `mvn -B verify` (unit, интеграционные и end-to-end тесты), отчёты тестов сохраняются как артефакт.

Учётные данные БД в workflow не хранятся. Нужно завести секреты репозитория
(Settings → Secrets and variables → Actions): `DB_URL`, `DB_USER`, `DB_PASSWORD`. Workflow подставляет их
в переменные окружения шага сборки и в контейнер PostgreSQL через `${{ secrets.* }}`.

## AI в проекте

- **Инструмент и для чего:** Claude (чат) — первичный каркас проекта и тестов, объяснение архитектуры, разбор ошибок компиляции, черновик CI-workflow для GitHub Actions. Сгенерированный код не принимался как есть: он собирался и запускался в IDE, найденные ошибки разбирались и исправлялись (см. ниже).
- **MCP:** `context7` — для работы с актуальной документацией Spring, Hibernate и Testcontainers; `postgres` — read-only доступ агента к схеме и данным локальной БД. Роль `mcp_readonly` создаётся скриптом `docker/postgres/init-readonly.sql`. Конфигурация находится в `.mcp.json` (Claude Code) и `.vscode/mcp.json` (Copilot). Строка подключения передаётся только через переменную `MCP_POSTGRES_URL`, например `postgresql://mcp_readonly:mcp_readonly@localhost:5432/expense_limiter`. MCP использовался для проверки реальной схемы, индексов и данных при отладке миграций и SQL-запросов.
- **Скиллы:** `.agents/skills/` (симлинк `.claude/skills`): `add-exchange-rate-provider`, `write-limit-test`, `create-db-migration`. Агент подхватывает их по `description`; например, запрос «добавь провайдера курсов alphavantage» использует скилл `add-exchange-rate-provider`, а «напиши тест на превышение лимита» — `write-limit-test`.
- **Что написано вручную:** бизнес-правила лимитов и расчёта расходов, модель домена, SQL-миграции и индексы, репозитории и критичные запросы, обработка конкурентного доступа через advisory-блокировки, стратегия работы с внешним API курсов и статусом `PENDING_RATE`, интеграционные и end-to-end тесты, CI-workflow, конфигурация Docker Compose и MCP.
- **Где AI ошибся и как это обнаружено:**
    1. `GlobalExceptionHandler`: в сгенерированном коде вызывался `PropertyNamingStrategies.SNAKE_CASE.translate(...)`. IDE подсветила ошибку `Cannot resolve method 'translate' in 'PropertyNamingStrategy'`. Причина: константа `SNAKE_CASE` объявлена с базовым типом `PropertyNamingStrategy`, у которого нет метода `translate`; он есть у `PropertyNamingStrategies.NamingBase`. Решение: создана константа типа `NamingBase` с экземпляром `SnakeCaseStrategy`.
    2. `TwelveDataClient`: при запуске приложения IDE показала `Class doesn't contain matching constructor for autowiring`. Причина: у класса два публичных конструктора, и Spring не может выбрать, каким создавать бин; второй конструктор принимает `AppProperties.TwelveData`, который не является бином. Решение: конструктор с `AppProperties` помечен `@Autowired`, второй оставлен для тестов.
    3. CI-workflow: в сгенерированном черновике URL, пользователь и пароль БД были прописаны прямо в YAML. Хардкод секретов убран из репозитория: значения вынесены в секреты GitHub (`DB_URL`, `DB_USER`, `DB_PASSWORD`), в workflow они подставляются через `${{ secrets.* }}`, а приложение читает те же значения из переменных окружения.