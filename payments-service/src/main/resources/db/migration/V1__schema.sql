CREATE TABLE accounts (
  id UUID PRIMARY KEY,
  owner_name TEXT NOT NULL,
  currency CHAR(3) NOT NULL CHECK (currency = 'EUR'),
  country CHAR(2) NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('CUSTOMER', 'SYSTEM')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account_balances (
  account_id UUID PRIMARY KEY REFERENCES accounts(id),
  balance_minor BIGINT NOT NULL DEFAULT 0,
  allow_negative BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT balance_not_negative CHECK (allow_negative OR balance_minor >= 0)
);

CREATE TABLE transfers (
  id UUID PRIMARY KEY,
  source_account_id UUID NOT NULL REFERENCES accounts(id),
  target_account_id UUID NOT NULL REFERENCES accounts(id),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  currency CHAR(3) NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('COMPLETED', 'REJECTED', 'IN_REVIEW')),
  reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (source_account_id <> target_account_id)
);

CREATE TABLE ledger_entries (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  transfer_id UUID NOT NULL REFERENCES transfers(id),
  account_id UUID NOT NULL REFERENCES accounts(id),
  direction TEXT NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
  amount_minor BIGINT NOT NULL CHECK (amount_minor > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (transfer_id, direction)
);

CREATE INDEX ledger_entries_account_idx ON ledger_entries (account_id, id DESC);