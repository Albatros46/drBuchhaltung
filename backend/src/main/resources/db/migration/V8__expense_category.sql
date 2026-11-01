-- Kontierung von Eingangsrechnungen: Kategorie (ExpenseCategory) oder eigenes Konto mit BU-Schluessel.
ALTER TABLE incoming_invoice ADD COLUMN expense_category VARCHAR(40);
ALTER TABLE incoming_invoice ADD COLUMN expense_account INTEGER;
ALTER TABLE incoming_invoice ADD COLUMN expense_bu_key VARCHAR(4);

-- Steuerkategorie je Position (S, Z, E, AE, K ...), um § 13b zu erkennen
ALTER TABLE incoming_invoice_line ADD COLUMN tax_category VARCHAR(4);

-- Zuletzt gewaehlte Kontierung des Lieferanten als Vorschlag fuer seine naechsten Rechnungen
ALTER TABLE supplier ADD COLUMN expense_category VARCHAR(40);
ALTER TABLE supplier ADD COLUMN expense_account INTEGER;
ALTER TABLE supplier ADD COLUMN expense_bu_key VARCHAR(4);
