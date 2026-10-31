-- Eingehende E-Rechnungen (Empfangspflicht seit 01.01.2025). Die Originaldatei wird unveraendert
-- aufbewahrt (GoBD); die uebrigen Spalten sind daraus gelesene Daten fuer Liste und Buchhaltung.
CREATE TABLE incoming_invoice (
    id                  UUID PRIMARY KEY,
    organization_id     UUID           NOT NULL REFERENCES organization (id),
    format              VARCHAR(20)    NOT NULL,
    document_type       VARCHAR(20)    NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    supplier_name       VARCHAR(300),
    supplier_street     VARCHAR(300),
    supplier_zip        VARCHAR(20),
    supplier_city       VARCHAR(200),
    supplier_country    VARCHAR(2),
    supplier_vat_id     VARCHAR(30),
    supplier_tax_id     VARCHAR(30),
    supplier_iban       VARCHAR(34),
    number              VARCHAR(200),
    issue_date          DATE,
    due_date            DATE,
    currency            VARCHAR(3),
    buyer_reference     VARCHAR(200),
    payment_terms       VARCHAR(2000),
    net_total           NUMERIC(14, 2) NOT NULL,
    vat_total           NUMERIC(14, 2) NOT NULL,
    gross_total         NUMERIC(14, 2) NOT NULL,
    validation_status   VARCHAR(20)    NOT NULL,
    validation_messages VARCHAR(4000),
    original_filename   VARCHAR(255)   NOT NULL,
    sha256              VARCHAR(64)    NOT NULL,
    paid_at             DATE,
    received_at         TIMESTAMP      NOT NULL,
    CONSTRAINT uq_incoming_invoice_file UNIQUE (organization_id, sha256)
);

-- Originaldatei getrennt, damit Listenabfragen sie nicht mitladen
CREATE TABLE incoming_invoice_file (
    incoming_invoice_id UUID PRIMARY KEY REFERENCES incoming_invoice (id) ON DELETE CASCADE,
    content_type        VARCHAR(100)   NOT NULL,
    content             BYTEA          NOT NULL
);

CREATE TABLE incoming_invoice_line (
    id                  UUID PRIMARY KEY,
    incoming_invoice_id UUID           NOT NULL REFERENCES incoming_invoice (id) ON DELETE CASCADE,
    position            INTEGER        NOT NULL,
    description         VARCHAR(1000),
    quantity            NUMERIC(16, 4),
    unit                VARCHAR(20),
    unit_price          NUMERIC(16, 4),
    vat_rate            NUMERIC(5, 2),
    net_amount          NUMERIC(14, 2)
);

CREATE INDEX ix_incoming_invoice_org_date ON incoming_invoice (organization_id, issue_date);
