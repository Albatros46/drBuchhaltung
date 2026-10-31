-- Lieferanten aus eingegangenen E-Rechnungen. Die Lieferantennummer ergibt im DATEV-Export
-- das Kreditorenkonto 70000 + Nummer. Wiedererkennung ueber match_key (USt-IdNr., Steuernummer oder Name).
CREATE TABLE supplier (
    id              UUID PRIMARY KEY,
    organization_id UUID         NOT NULL REFERENCES organization (id),
    supplier_number INTEGER      NOT NULL,
    match_key       VARCHAR(300) NOT NULL,
    name            VARCHAR(300),
    vat_id          VARCHAR(30),
    tax_id          VARCHAR(30),
    created_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uq_supplier_number UNIQUE (organization_id, supplier_number),
    CONSTRAINT uq_supplier_match UNIQUE (organization_id, match_key)
);

ALTER TABLE incoming_invoice ADD COLUMN supplier_id UUID REFERENCES supplier (id);
