CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  email VARCHAR(255) NOT NULL UNIQUE,
  phone VARCHAR(32) NOT NULL UNIQUE,
  kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (kyc_status IN ('PENDING','VERIFIED','REJECTED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE accounts (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT REFERENCES users(id),
  name VARCHAR(120) NOT NULL,
  account_type VARCHAR(24) NOT NULL CHECK (account_type IN ('WALLET','CASH_IN','CASH_OUT','FEE_REVENUE')),
  currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
  balance_paise BIGINT NOT NULL DEFAULT 0,
  is_system BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK ((account_type = 'WALLET' AND user_id IS NOT NULL AND is_system = FALSE) OR (account_type <> 'WALLET' AND user_id IS NULL AND is_system = TRUE))
);
CREATE INDEX idx_accounts_user ON accounts(user_id, id) WHERE user_id IS NOT NULL;
CREATE TABLE transfers (
  id BIGSERIAL PRIMARY KEY,
  transfer_type VARCHAR(24) NOT NULL CHECK (transfer_type IN ('TOP_UP','PEER_TRANSFER','WITHDRAWAL','FEE','REVERSAL')),
  status VARCHAR(24) NOT NULL CHECK (status IN ('PENDING','COMPLETED','HELD','REJECTED','REVERSED')),
  amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
  currency CHAR(3) NOT NULL DEFAULT 'INR' CHECK (currency = 'INR'),
  from_account_id BIGINT REFERENCES accounts(id),
  to_account_id BIGINT REFERENCES accounts(id),
  original_transfer_id BIGINT REFERENCES transfers(id),
  note TEXT,
  risk_flag BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at TIMESTAMPTZ,
  CHECK (from_account_id IS DISTINCT FROM to_account_id)
);
CREATE INDEX idx_transfers_created ON transfers(created_at DESC, id DESC);
CREATE INDEX idx_transfers_from_created ON transfers(from_account_id, created_at DESC, id DESC);
CREATE INDEX idx_transfers_to_created ON transfers(to_account_id, created_at DESC, id DESC);
CREATE TABLE ledger_entries (
  id BIGSERIAL PRIMARY KEY,
  transfer_id BIGINT NOT NULL REFERENCES transfers(id),
  account_id BIGINT NOT NULL REFERENCES accounts(id),
  direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  amount_paise BIGINT NOT NULL CHECK (amount_paise > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_account_created ON ledger_entries(account_id, created_at DESC, id DESC);
CREATE INDEX idx_ledger_transfer ON ledger_entries(transfer_id);
CREATE TABLE idempotency_keys (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  idempotency_key VARCHAR(100) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(16) NOT NULL CHECK (status IN ('PROCESSING','COMPLETED')),
  response_code INTEGER,
  response_body JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(user_id, idempotency_key)
);
CREATE INDEX idx_idempotency_created ON idempotency_keys(created_at);
CREATE TABLE transfer_status_audit (
  id BIGSERIAL PRIMARY KEY,
  transfer_id BIGINT NOT NULL REFERENCES transfers(id),
  old_status VARCHAR(24),
  new_status VARCHAR(24) NOT NULL,
  changed_by VARCHAR(120) NOT NULL,
  reason TEXT,
  changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transfer_audit_transfer ON transfer_status_audit(transfer_id, changed_at DESC);
CREATE TABLE risk_rules (
  id BIGSERIAL PRIMARY KEY,
  rule_code VARCHAR(64) NOT NULL UNIQUE,
  description TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  threshold_paise BIGINT,
  window_minutes INTEGER,
  action VARCHAR(16) NOT NULL CHECK (action IN ('HOLD','FLAG')),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE risk_flags (
  id BIGSERIAL PRIMARY KEY,
  transfer_id BIGINT NOT NULL REFERENCES transfers(id),
  rule_code VARCHAR(64) NOT NULL,
  explanation TEXT,
  label VARCHAR(24) CHECK (label IN ('likely_ok','review','likely_fraud')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(transfer_id, rule_code)
);
CREATE TABLE reconciliation_runs (
  id BIGSERIAL PRIMARY KEY,
  started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ended_at TIMESTAMPTZ,
  status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','OK','MISMATCH','FAILED','SKIPPED')),
  checks_run INTEGER NOT NULL DEFAULT 0,
  problems_found INTEGER NOT NULL DEFAULT 0,
  details TEXT
);
CREATE TABLE reconciliation_problems (
  id BIGSERIAL PRIMARY KEY,
  run_id BIGINT NOT NULL REFERENCES reconciliation_runs(id),
  check_name VARCHAR(80) NOT NULL,
  entity_id BIGINT,
  expected_value TEXT,
  actual_value TEXT,
  details TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_recon_problems_run ON reconciliation_problems(run_id);
CREATE TABLE job_locks (
  job_name VARCHAR(100) PRIMARY KEY,
  touched_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO job_locks(job_name) VALUES ('nightly-reconciliation');
INSERT INTO risk_rules(rule_code,description,enabled,threshold_paise,window_minutes,action) VALUES
 ('LARGE_TRANSFER','Transfer amount exceeds configured threshold',TRUE,5000000,NULL,'HOLD'),
 ('VELOCITY_10M','More than 5 outgoing transfers in 10 minutes',TRUE,NULL,10,'FLAG');
-- Ledger entries are append-only. Corrections must use a reversal transfer.
CREATE FUNCTION prevent_ledger_mutation() RETURNS trigger AS $$
BEGIN RAISE EXCEPTION 'ledger_entries are append-only; use a reversal transfer'; END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER ledger_no_update_delete BEFORE UPDATE OR DELETE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();
-- Protect stored wallet balances from going negative. System accounts may be negative.
ALTER TABLE accounts ADD CONSTRAINT wallet_balance_nonnegative CHECK (account_type <> 'WALLET' OR balance_paise >= 0);
