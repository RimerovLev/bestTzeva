-- ─────────────────────────────────────────────────────────────────────────────
-- R-17: provider_transaction_id имел обычный индекс, но не был уникален.
-- Обработка callback от эквайринга (T-8.3) ищет платёж по нему, а провайдеры
-- ретраят callback — без уникальности повторный callback мог создать второй
-- платёж вместо конфликта. Добавляем частичный уникальный индекс по паре
-- (provider, provider_transaction_id); null (платёж ещё без внешнего id) не
-- ограничивается.
-- ─────────────────────────────────────────────────────────────────────────────

create unique index uq_payments_provider_txn
    on payments (provider, provider_transaction_id)
    where provider_transaction_id is not null;
