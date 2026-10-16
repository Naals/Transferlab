CREATE TABLE accounts (
                          id UUID PRIMARY KEY,
                          owner_id UUID,
                          kind VARCHAR(16) NOT NULL,
                          currency VARCHAR(3) NOT NULL DEFAULT 'KZT',
                          balance_minor BIGINT NOT NULL DEFAULT 0,
                          status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
                          created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

                          CONSTRAINT accounts_kind_check
                              CHECK (kind IN ('CUSTOMER', 'SYSTEM')),

                          CONSTRAINT accounts_currency_check
                              CHECK (currency = 'KZT'),

                          CONSTRAINT accounts_status_check
                              CHECK (status IN ('ACTIVE', 'FROZEN')),

                          CONSTRAINT accounts_owner_check
                              CHECK (
                                  (kind = 'CUSTOMER' AND owner_id IS NOT NULL)
                                      OR
                                  (kind = 'SYSTEM' AND owner_id IS NULL)
                                  ),

                          CONSTRAINT customer_balance_nonnegative
                              CHECK (kind <> 'CUSTOMER' OR balance_minor >= 0),

                          CONSTRAINT one_customer_account_per_owner
                              UNIQUE (owner_id)
);

CREATE TABLE transfers (
                           id UUID PRIMARY KEY,
                           source_account_id UUID NOT NULL REFERENCES accounts(id),
                           destination_account_id UUID NOT NULL REFERENCES accounts(id),
                           amount_minor BIGINT NOT NULL,
                           currency VARCHAR(3) NOT NULL,
                           posted_at TIMESTAMPTZ NOT NULL,

                           CONSTRAINT transfer_amount_positive
                               CHECK (amount_minor > 0),

                           CONSTRAINT transfer_currency_check
                               CHECK (currency = 'KZT'),

                           CONSTRAINT transfer_accounts_different
                               CHECK (source_account_id <> destination_account_id)
);

CREATE TABLE journal_transactions (
                                      id UUID PRIMARY KEY,
                                      transfer_id UUID UNIQUE REFERENCES transfers(id),
                                      kind VARCHAR(16) NOT NULL,
                                      posted_at TIMESTAMPTZ NOT NULL,

                                      CONSTRAINT journal_kind_check
                                          CHECK (kind IN ('TRANSFER', 'FUNDING')),

                                      CONSTRAINT journal_reference_check
                                          CHECK (
                                              (kind = 'TRANSFER' AND transfer_id IS NOT NULL)
                                                  OR
                                              (kind = 'FUNDING' AND transfer_id IS NULL)
                                              )
);

CREATE TABLE journal_entries (
                                 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 journal_id UUID NOT NULL REFERENCES journal_transactions(id),
                                 account_id UUID NOT NULL REFERENCES accounts(id),
                                 direction VARCHAR(6) NOT NULL,
                                 amount_minor BIGINT NOT NULL,

                                 CONSTRAINT entry_direction_check
                                     CHECK (direction IN ('DEBIT', 'CREDIT')),

                                 CONSTRAINT entry_amount_positive
                                     CHECK (amount_minor > 0)
);

CREATE INDEX journal_entries_journal_idx
    ON journal_entries (journal_id);

CREATE INDEX journal_entries_account_idx
    ON journal_entries (account_id, id);

CREATE INDEX transfers_source_idx
    ON transfers (source_account_id, posted_at, id);

CREATE INDEX transfers_destination_idx
    ON transfers (destination_account_id, posted_at, id);