-- XRechnung verlangt einen Ansprechpartner des Verkaeufers mit Telefon und E-Mail (BT-41, BT-42, BT-43).
ALTER TABLE organization ADD COLUMN contact_name VARCHAR(100);
ALTER TABLE organization ADD COLUMN phone VARCHAR(40);
