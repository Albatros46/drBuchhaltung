-- Firmenlogo fuer den Briefkopf der Belege; getrennt gespeichert, damit Firmendaten ohne Bild geladen werden.
CREATE TABLE organization_logo (
    organization_id UUID PRIMARY KEY REFERENCES organization (id) ON DELETE CASCADE,
    content_type    VARCHAR(20)  NOT NULL,
    content         BYTEA        NOT NULL,
    width           INTEGER      NOT NULL,
    height          INTEGER      NOT NULL,
    updated_at      TIMESTAMP    NOT NULL
);
