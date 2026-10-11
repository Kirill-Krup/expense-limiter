create table exchange_rate (
    id          bigserial      primary key,
    currency    varchar(3)     not null,
    rate_date   date           not null,
    rate        numeric(19,10) not null check (rate > 0),
    source_date date           not null,
    source      varchar(32)    not null,
    created_at  timestamptz    not null default now(),
    constraint uq_exchange_rate unique (currency, rate_date)
);

create table spending_limit (
    id               bigserial     primary key,
    account          varchar(10)   not null,
    expense_category varchar(16)   not null,
    limit_sum        numeric(19,2) not null check (limit_sum > 0),
    set_at           timestamptz   not null,
    constraint uq_spending_limit unique (account, expense_category, set_at)
);

create table expense_transaction (
    id                bigserial     primary key,
    account_from      varchar(10)   not null,
    account_to        varchar(10)   not null,
    currency          varchar(3)    not null,
    amount            numeric(19,2) not null check (amount > 0),
    expense_category  varchar(16)   not null,
    tx_datetime       timestamptz   not null,
    status            varchar(16)   not null,
    amount_usd        numeric(19,2),
    exchange_rate_id  bigint        references exchange_rate (id),
    applied_limit_id  bigint        references spending_limit (id),
    applied_limit_sum numeric(19,2),
    limit_exceeded    boolean       not null default false,
    created_at        timestamptz   not null default now()
);

create index ix_tx_month_spend on expense_transaction (account_from, expense_category, tx_datetime)
    where status = 'PROCESSED';
create index ix_tx_exceeded on expense_transaction (account_from, tx_datetime)
    where limit_exceeded;
create index ix_tx_pending on expense_transaction (id)
    where status = 'PENDING_RATE';
