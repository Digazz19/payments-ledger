CREATE TABLE idempotency_keys (
  idempotency_key TEXT PRIMARY KEY,
  request_hash TEXT NOT NULL,
  state TEXT NOT NULL CHECK (state IN ('IN_PROGRESS', 'DONE')),
  response_status INT,
  response_body TEXT,
  locked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);