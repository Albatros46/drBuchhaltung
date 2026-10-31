-- Versandnachweis: wann und an wen ein Beleg zuletzt per E-Mail verschickt wurde
ALTER TABLE invoice ADD COLUMN sent_at TIMESTAMP;
ALTER TABLE invoice ADD COLUMN sent_to VARCHAR(500);
