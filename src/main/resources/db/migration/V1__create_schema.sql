-- Wallets table
CREATE TABLE wallets (
    id         UUID        PRIMARY KEY,
    balance    BIGINT      NOT NULL DEFAULT 0 CHECK (balance >= 0),
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transfers table
CREATE TABLE transfers (
    id              UUID        PRIMARY KEY,
    from_wallet_id  UUID        NOT NULL REFERENCES wallets(id),
    to_wallet_id    UUID        NOT NULL REFERENCES wallets(id),
    amount          BIGINT      NOT NULL CHECK (amount > 0),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_no_self_transfer CHECK (from_wallet_id <> to_wallet_id)
);

CREATE INDEX idx_transfers_from_wallet ON transfers(from_wallet_id);
CREATE INDEX idx_transfers_to_wallet   ON transfers(to_wallet_id);

-- Ledger entries table (double-entry bookkeeping)
CREATE TABLE ledger_entries (
    id          UUID        PRIMARY KEY,
    wallet_id   UUID        NOT NULL REFERENCES wallets(id),
    transfer_id UUID        NOT NULL REFERENCES transfers(id),
    entry_type  VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount      BIGINT      NOT NULL CHECK (amount > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ledger_wallet   ON ledger_entries(wallet_id);
CREATE INDEX idx_ledger_transfer ON ledger_entries(transfer_id);

-- Idempotency records table
CREATE TABLE idempotency_records (
    id              UUID         PRIMARY KEY,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    transfer_id     UUID         NOT NULL REFERENCES transfers(id),
    response_code   INTEGER      NOT NULL,
    response_body   TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_idempotency_key ON idempotency_records(idempotency_key);
