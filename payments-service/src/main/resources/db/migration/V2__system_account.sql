INSERT INTO accounts (id, owner_name, currency, country, type)
VALUES ('00000000-0000-0000-0000-000000000001', 'System', 'EUR', 'PT', 'SYSTEM');

INSERT INTO account_balances (account_id, balance_minor, allow_negative)
VALUES ('00000000-0000-0000-0000-000000000001', 0, TRUE);