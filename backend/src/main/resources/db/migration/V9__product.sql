-- Artikel / Leistungen: Vorlagen fuer Rechnungspositionen. Positionen kopieren die Werte,
-- daher bleiben ausgestellte Belege unveraendert, wenn ein Artikel geaendert oder geloescht wird.
CREATE TABLE product (
    id              UUID PRIMARY KEY,
    organization_id UUID          NOT NULL REFERENCES organization (id),
    product_number  INTEGER       NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     VARCHAR(1000),
    unit            VARCHAR(20)   NOT NULL,
    unit_price      DECIMAL(12, 2) NOT NULL,
    vat_rate        DECIMAL(5, 2) NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    CONSTRAINT uq_product_number UNIQUE (organization_id, product_number)
);
