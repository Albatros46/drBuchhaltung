-- Mahnwesen: Zahlungserinnerung, 1. und 2. Mahnung je Rechnung.
ALTER TABLE organization ADD COLUMN dunning_fee NUMERIC(10, 2) NOT NULL DEFAULT 0;

CREATE TABLE dunning (
    id              UUID PRIMARY KEY,
    organization_id UUID           NOT NULL REFERENCES organization (id),
    invoice_id      UUID           NOT NULL REFERENCES invoice (id),
    level           VARCHAR(30)    NOT NULL,
    issue_date      DATE           NOT NULL,
    deadline        DATE           NOT NULL,
    open_amount     NUMERIC(14, 2) NOT NULL,
    fee             NUMERIC(10, 2) NOT NULL,
    sent_at         TIMESTAMP,
    sent_to         VARCHAR(500),
    created_at      TIMESTAMP      NOT NULL,
    CONSTRAINT uq_dunning_level UNIQUE (invoice_id, level)
);

CREATE INDEX ix_dunning_invoice ON dunning (invoice_id);
