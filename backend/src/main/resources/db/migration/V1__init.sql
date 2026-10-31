CREATE TABLE organization (
    id                  UUID PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    street              VARCHAR(200),
    zip                 VARCHAR(10),
    city                VARCHAR(100),
    country             VARCHAR(2)   NOT NULL DEFAULT 'DE',
    steuernummer        VARCHAR(30),
    ust_id              VARCHAR(20),
    kleinunternehmer    BOOLEAN      NOT NULL DEFAULT FALSE,
    kontenrahmen        VARCHAR(5)   NOT NULL DEFAULT 'SKR03',
    iban                VARCHAR(34),
    bic                 VARCHAR(11),
    email               VARCHAR(200),
    datev_berater_nr    INTEGER,
    datev_mandant_nr    INTEGER,
    created_at          TIMESTAMP    NOT NULL
);

CREATE TABLE app_user (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    email           VARCHAR(200) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP    NOT NULL
);

CREATE TABLE customer (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    customer_number INTEGER      NOT NULL,
    name            VARCHAR(200) NOT NULL,
    street          VARCHAR(200),
    zip             VARCHAR(10),
    city            VARCHAR(100),
    country         VARCHAR(2)   NOT NULL DEFAULT 'DE',
    email           VARCHAR(200),
    ust_id          VARCHAR(20),
    leitweg_id      VARCHAR(50),
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uq_customer_number UNIQUE (organization_id, customer_number)
);

-- Lueckenlose Nummernkreise je Organisation, Belegart und Jahr (§14 UStG)
CREATE TABLE number_sequence (
    organization_id UUID        NOT NULL REFERENCES organization (id),
    doc_type        VARCHAR(20) NOT NULL,
    doc_year        INTEGER     NOT NULL,
    last_value      INTEGER     NOT NULL,
    PRIMARY KEY (organization_id, doc_type, doc_year)
);

CREATE TABLE invoice (
    id                  UUID PRIMARY KEY,
    organization_id     UUID          NOT NULL REFERENCES organization (id),
    customer_id         UUID          NOT NULL REFERENCES customer (id),
    doc_type            VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL,
    tax_mode            VARCHAR(20)   NOT NULL,
    number              VARCHAR(30),
    issue_date          DATE,
    service_date_from   DATE,
    service_date_to     DATE,
    due_date            DATE,
    intro_text          VARCHAR(2000),
    net_total           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    vat_total           NUMERIC(14, 2) NOT NULL DEFAULT 0,
    gross_total         NUMERIC(14, 2) NOT NULL DEFAULT 0,
    cancels_invoice_id  UUID REFERENCES invoice (id),
    paid_at             DATE,
    locked_at           TIMESTAMP,
    created_at          TIMESTAMP     NOT NULL,
    CONSTRAINT uq_invoice_number UNIQUE (organization_id, number)
);

CREATE TABLE invoice_line (
    id          UUID PRIMARY KEY,
    invoice_id  UUID           NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    position    INTEGER        NOT NULL,
    description VARCHAR(500)   NOT NULL,
    quantity    NUMERIC(12, 3) NOT NULL,
    unit        VARCHAR(20)    NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL,
    vat_rate    NUMERIC(5, 2)  NOT NULL,
    net_amount  NUMERIC(14, 2) NOT NULL
);

-- GoBD: unveraenderliches Protokoll aller relevanten Aenderungen
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL REFERENCES organization (id),
    entity_type     VARCHAR(30)   NOT NULL,
    entity_id       UUID          NOT NULL,
    action          VARCHAR(30)   NOT NULL,
    detail          VARCHAR(1000),
    actor           VARCHAR(200)  NOT NULL,
    created_at      TIMESTAMP     NOT NULL
);

CREATE INDEX ix_invoice_org_date ON invoice (organization_id, issue_date);
CREATE INDEX ix_customer_org ON customer (organization_id);
