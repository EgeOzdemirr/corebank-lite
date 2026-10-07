-- A transfer as transfer-service recorded it. CREATED never reaches the database: a request that fails validation
-- never becomes a row, and an accepted one is PENDING_APPROVAL or APPROVED before its first commit.
CREATE TABLE transfer (
    id                UUID PRIMARY KEY,
    source_account_id UUID           NOT NULL,
    source_iban       VARCHAR(26)    NOT NULL,
    target_account_id UUID           NOT NULL,
    target_iban       VARCHAR(26)    NOT NULL,
    beneficiary_name  VARCHAR(140)   NOT NULL,
    amount            NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    currency          VARCHAR(3)     NOT NULL,
    channel           VARCHAR(32)    NOT NULL
        CHECK (channel IN ('BRANCH', 'INTERNET_BANKING', 'MOBILE', 'OPEN_BANKING', 'API')),
    maker_user_id     VARCHAR(64)    NOT NULL,
    checker_user_id   VARCHAR(64),
    approval_required BOOLEAN        NOT NULL,
    status            VARCHAR(16)    NOT NULL
        CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'POSTED', 'FAILED', 'REVERSED')),
    failure_code      VARCHAR(64),
    -- Istanbul business day the amount was reserved against; a failure gives it back to this day, not to today.
    business_day      DATE           NOT NULL,
    requested_at      TIMESTAMPTZ    NOT NULL,
    approved_at       TIMESTAMPTZ,
    posted_at         TIMESTAMPTZ,
    failed_at         TIMESTAMPTZ,
    reversed_at       TIMESTAMPTZ,
    version           BIGINT         NOT NULL,
    CONSTRAINT transfer_distinct_accounts_ck CHECK (source_account_id <> target_account_id),
    -- Segregation of duties, also enforced by the domain.
    CONSTRAINT transfer_checker_is_not_maker_ck CHECK (checker_user_id IS NULL OR checker_user_id <> maker_user_id),
    CONSTRAINT transfer_checker_needs_approval_ck CHECK (approval_required OR checker_user_id IS NULL),
    CONSTRAINT transfer_failure_code_iff_failed_ck CHECK ((status = 'FAILED') = (failure_code IS NOT NULL))
);

-- The expiry job (roadmap week 3) looks for PENDING_APPROVAL transfers of past business days.
CREATE INDEX transfer_status_business_day_ix ON transfer (status, business_day);
CREATE INDEX transfer_source_requested_ix ON transfer (source_account_id, requested_at DESC);

-- Running total per source account, business day and currency. The limit check and the increment are one atomic
-- statement (see JdbcDailyUsageLedger), so concurrent transfers cannot overshoot the daily limit together.
CREATE TABLE daily_transfer_usage (
    source_account_id UUID           NOT NULL,
    business_day      DATE           NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    reserved          NUMERIC(19, 2) NOT NULL CHECK (reserved >= 0),
    PRIMARY KEY (source_account_id, business_day, currency)
);

-- Transfers that need a human (ADR-0005). Automatic recovery must skip them.
CREATE TABLE transfer_review (
    transfer_id UUID        NOT NULL REFERENCES transfer (id),
    reason      VARCHAR(64) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (transfer_id, reason)
);

CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(64)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(64)  NOT NULL,
    schema_version INTEGER      NOT NULL,
    topic          VARCHAR(128) NOT NULL,
    partition_key  VARCHAR(64)  NOT NULL,
    payload        JSONB        NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ
);

-- The relay (roadmap week 3) polls only unpublished rows, oldest first.
CREATE INDEX outbox_event_unpublished_ix ON outbox_event (occurred_at) WHERE published_at IS NULL;
