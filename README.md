# Rechnio (drBuchhaltung)

Rechnungen, Angebote, Stornos und DATEV-Export für kleine Unternehmen in Deutschland.
Ein Spring-Boot-Backend und eine Expo-App (iOS, Android und Web aus einem Code).

```
Rechnio/
├─ backend/   Spring Boot 3.5 · Java 21 · H2 (lokal) / PostgreSQL (Produktion) · JWT
└─ app/       Expo SDK 58 · Expo Router · TypeScript · NativeWind 4 · React Native Reusables
```

UI-Bausteine liegen in `app/src/components/ui/` (React Native Reusables, per CLI hinzugefügt).
Weitere Komponenten: `npx @react-native-reusables/cli@latest add <name>` im Ordner `app/`.

## Lokal starten

Voraussetzungen: JDK 21 oder neuer, Maven, Node.js.

**Backend** (Port 8080, Daten in `backend/data/`):

```bash
cd backend
mvn spring-boot:run
```

Auch beim Start im Projektordner (z. B. IntelliJ) wird die Datenbank in `backend/data/` verwendet.
Anderer Ordner: `-Drechnio.data-dir=...`.

**App** (zweites Terminal):

```bash
cd app
npm run web        # Browser
npm run android    # Android-Emulator oder Expo Go
```

Auf einem echten Handy erreicht die App den Rechner nicht über `localhost`. Dann vorher die LAN-IP setzen:

```bash
set EXPO_PUBLIC_API_URL=http://192.168.0.10:8080
```

Erster Ablauf in der App: Konto erstellen → Firmendaten ausfüllen (Firmenname oben rechts antippen: Anschrift,
Steuernummer) → Kunde anlegen → optional Artikel anlegen → Neue Rechnung → Ausstellen.

Das Menü oben führt zu Übersicht, Neue Rechnung, Kunden, Artikel, Eingangsrechnungen und Export
(DATEV sowie Kunden- und Artikellisten als CSV für Excel).

### Beispieldaten

Bei laufendem Backend füllt `node scripts/seed-demo.mjs` die lokale Datenbank über die API:
10 Kunden, 10 überfällige Rechnungen mit Zahlungserinnerung, bezahlte und offene Rechnungen, ein Storno,
Angebote, Entwürfe sowie 10 Lieferanten-Konten, deren XRechnungen bzw. ZUGFeRD-PDFs als
Eingangsrechnungen (mit Kontierung) beim Demo-Konto eingehen. Alle Firmen und Adressen sind erfunden.
Zugangsdaten stehen oben im Skript; ein zweiter Lauf legt nichts doppelt an.
Hinweis: Rechnungen werden immer mit dem heutigen Datum ausgestellt; die überfälligen haben daher ein
Fälligkeitsdatum vor dem Rechnungsdatum.

## Backend-Struktur

```
de.rechnio
├─ controller      REST-Endpunkte, ohne Geschäftslogik
├─ service         Schnittstellen
│  └─ impl         Implementierungen
├─ repository      Spring-Data-Repositories
├─ entity          JPA-Entitäten
├─ enums           DocType, InvoiceStatus, TaxMode, Kontenrahmen
├─ dto             Request- und Response-Records
├─ config          Security, JWT, CurrentUser
├─ exception       Fehler und globaler Handler
└─ util            VatCalculator
```

## API

| Methode | Pfad | Zweck |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | Konto anlegen, anmelden (JWT) |
| GET/PUT | `/api/organization` | Firmenstammdaten |
| GET/POST/PUT/DELETE | `/api/customers` | Kunden; Löschen nur ohne Belege (GoBD, sonst 409) |
| GET/POST/PUT/DELETE | `/api/products` | Artikel/Leistungen als Vorlagen für Positionen |
| GET | `/api/customers/export`, `/api/products/export` | Kunden- bzw. Artikelliste als CSV (UTF-8, Semikolon) |
| GET/POST/PUT/DELETE | `/api/invoices` | Entwürfe von Rechnungen und Angeboten |
| GET/POST/DELETE | `/api/organization/logo` | Firmenlogo (PNG/JPEG bis 1 MB) für den Briefkopf aller Belege |
| GET | `/api/invoices/{id}/pdf` | Beleg als PDF/A-3b (DIN 5008; Entwürfe als ENTWURF markiert) |
| GET | `/api/invoices/{id}/zugferd` | E-Rechnung ZUGFeRD / Factur-X (EN16931): PDF/A-3 mit eingebettetem XML |
| GET | `/api/invoices/{id}/xrechnung` | E-Rechnung XRechnung 3.0 (CII-XML) |
| POST | `/api/invoices/{id}/issue` | Ausstellen: Nummer vergeben, festschreiben |
| POST | `/api/invoices/{id}/paid` | Als bezahlt markieren |
| POST | `/api/invoices/{id}/cancel` | Stornorechnung erzeugen |
| POST | `/api/invoices/{id}/convert` | Angebot in Rechnung umwandeln |
| POST | `/api/incoming-invoices` (multipart `file`) | Eingehende E-Rechnung einlesen und prüfen |
| GET | `/api/incoming-invoices`, `/api/incoming-invoices/{id}` | Eingangsrechnungen |
| POST | `/api/incoming-invoices/{id}/paid` | Eingangsrechnung als bezahlt markieren |
| GET | `/api/incoming-invoices/{id}/original` | Unveränderte Originaldatei |
| GET/PUT | `/api/incoming-invoices/expense-categories`, `/api/incoming-invoices/{id}/expense` | Kontierung (Gegenkonto für DATEV) |
| GET | `/api/exports/datev?from=2026-01-01&to=2026-12-31` | DATEV-Buchungsstapel (EXTF CSV) |
| GET | `/api/exports/datev/stammdaten` | DATEV-Stammdaten Debitoren/Kreditoren (EXTF CSV) |

## Fachliche Regeln

- Pflichtangaben nach § 14 UStG werden vor dem Ausstellen geprüft (HTTP 422 mit Liste der fehlenden Angaben).
- Lückenlose Nummernkreise je Jahr und Belegart: `RE-2026-0001`, `AN-…`, `ST-…`.
- Ausgestellte Belege sind unveränderlich; Korrekturen nur per Storno (GoBD). Änderungen stehen im `audit_log`.
- § 19 (Kleinunternehmer) und § 13b (Reverse-Charge) setzen den Steuersatz auf 0 % und liefern den Pflichthinweis.
- DATEV-Export (ein Buchungsstapel für Rechnungsausgang und -eingang, je Steuersatz eine Buchung):
  - Ausgang: Debitor `10000 + Kundennummer` (Soll) an Erlös-Automatikkonto (SKR03 8400/8300, SKR04 4400/4300).
  - Eingang: Kreditor `70000 + Lieferantennummer` (Haben) an das Gegenkonto der Kontierung (siehe unten);
    Gutschriften der Lieferanten im Soll. Lieferanten werden über USt-IdNr., Steuernummer oder Namen
    wiedererkannt und behalten ihre Nummer.
  - Stammdaten getrennt: `GET /api/exports/datev/stammdaten` liefert alle Debitoren und Kreditoren im
    DATEV-Format *Debitoren/Kreditoren* (EXTF, Kategorie 16, Version 5, 254 Felder) mit Name, Kurzbezeichnung,
    USt-IdNr., Anschrift, E-Mail, IBAN und Kunden-/Lieferantennummer. Lieferanten-Anschrift und IBAN stammen
    aus der zuletzt eingegangenen Rechnung. Adressatentyp ist „keine Angabe“, da Rechnio Firmen und
    Privatpersonen nicht unterscheidet. Vor dem Buchungsstapel importieren.
  **Vor dem produktiven Einsatz einmal mit dem Steuerberater probeweise in DATEV importieren.**

### Kontierung von Eingangsrechnungen

Jede Eingangsrechnung hat eine Kontierung, die das Gegenkonto im DATEV-Export bestimmt
(App: Eingangsrechnung → *Kontierung ändern*).

- Vorschlag ohne Auswahl: Wareneingang (Automatikkonten SKR03 3400/3300/3200, SKR04 5400/5300/5200).
- Wählbar sind u. a. Fremdleistungen, Miete, Bürobedarf, Porto, Telefon, Software, Kfz, Reisekosten,
  Werbung, Versicherungen, Fortbildung, Reparaturen, Beratung, Buchführung, Bankgebühren und GWG.
  Diese Konten sind keine Automatikkonten: die Vorsteuer kommt über den BU-Schlüssel (9 = 19 %, 8 = 7 %);
  Versicherungen und Bankgebühren werden ohne Vorsteuer gebucht.
- **§ 13b:** Eingangsrechnungen mit Steuerkategorie *AE* werden als Reverse Charge erkannt und auf das
  Automatikkonto gebucht (inländischer Bauleistender SKR03 3120 / SKR04 5920, EU-Ausland 3123 / 5923).
- **Eigenes Konto:** beliebige Kontonummer mit optionalem BU-Schlüssel, wenn keine Kategorie passt.
- *Für diesen Lieferanten merken* übernimmt die Wahl für künftige Rechnungen desselben Lieferanten
  (nicht bei Reverse Charge). Jede Änderung steht im `audit_log`.
- API: `GET /api/incoming-invoices/expense-categories`, `PUT /api/incoming-invoices/{id}/expense`
  mit `{"category":"BUEROBEDARF","rememberForSupplier":true}` bzw. `{"category":"CUSTOM","account":4980,"buKey":"9"}`.

**Die Kontenzuordnung ist ein Vorschlag, vor allem im SKR04. Bitte vor dem ersten Export mit dem
Steuerberater abstimmen** und ggf. *Eigenes Konto* verwenden.

## E-Rechnung

- Erzeugt mit [Mustangproject](https://www.mustangproject.org/); jede Testrechnung wird mit dem Mustang-Validator
  gegen XSD, EN 16931- und XRechnung-Schematron sowie PDF/A geprüft (`ERechnungIntegrationTest`).
- Nur für ausgestellte Rechnungen und Stornos. Ein Storno wird als Gutschrift (Typ 381) mit Bezug auf die
  Originalrechnung exportiert.
- XRechnung verlangt zusätzlich: Ansprechpartner, Telefon und E-Mail der Firma, E-Mail des Kunden und IBAN.
  Fehlt etwas, antwortet die API mit HTTP 422 und einer Liste der fehlenden Angaben.
- Käuferreferenz (BT-10): Leitweg-ID des Kunden, sonst die Kundennummer.
- Schrift im PDF: Liberation Sans (SIL Open Font License, `backend/src/main/resources/fonts/`).

**Empfang:** ZUGFeRD-PDF, XRechnung (CII und UBL) sowie sonstiges EN-16931-XML werden eingelesen.
Jede Datei wird beim Upload geprüft (XSD, Schematron, PDF/A); formal fehlerhafte Rechnungen werden
trotzdem gespeichert und mit den Befunden markiert. Die Originaldatei bleibt unverändert erhalten (GoBD),
doppelte Uploads werden über SHA-256 erkannt. Normale PDFs ohne eingebettetes XML werden abgelehnt.

## Versand per E-Mail

`GET /api/invoices/{id}/email` liefert einen Vorschlag (Empfänger, Betreff, Text, Anhang),
`POST /api/invoices/{id}/send` verschickt den Beleg und vermerkt Zeitpunkt und Empfänger.
Anhang: ZUGFeRD-PDF (Standard), PDF oder XRechnung (Standard, wenn der Kunde eine Leitweg-ID hat).
Angebote gehen immer als PDF. Fehlen Angaben für die E-Rechnung, wird nichts verschickt (HTTP 422).

**Lokal testen:** Ohne Konfiguration sendet das Backend an `localhost:1025`. Dort ein Test-Postfach starten,
z. B. [Mailpit](https://mailpit.axllent.org/) (`mailpit.exe` oder `docker run -p 8025:8025 -p 1025:1025 axllent/mailpit`),
und die Mails unter http://localhost:8025 ansehen. Es wird nichts an echte Empfänger zugestellt.

**Echter Versand** über einen SMTP-Anbieter per Umgebungsvariablen:

| Variable | Beispiel |
|---|---|
| `MAIL_HOST`, `MAIL_PORT` | `smtp.example.de`, `587` |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | Zugangsdaten des Postfachs |
| `MAIL_AUTH`, `MAIL_STARTTLS` | `true`, `true` |
| `MAIL_FROM` | Absender, den der Anbieter erlaubt (die Firmen-E-Mail wird als Reply-To gesetzt) |

## Mahnwesen

| Stufe | Frist | Gebühr |
|---|---|---|
| Zahlungserinnerung | 10 Tage | keine |
| 1. Mahnung | 7 Tage | Mahngebühr aus *Firma → Mahnwesen* |
| 2. Mahnung | 7 Tage | Mahngebühr |

- Nur für offene, überfällige Rechnungen; die nächste Stufe erst nach Ablauf der Frist der vorherigen.
- Jede Stufe als DIN-5008-PDF mit Rechnungsbezug, Gebühren und offenem Gesamtbetrag; Versand per E-Mail.
- Nach der 2. Mahnung keine weitere Stufe: gerichtliches Mahnverfahren o. Ä. außerhalb der App.
- Verzugszinsen werden nicht berechnet; Mahngebühren erscheinen nicht im DATEV-Export.
- API: `GET/POST /api/invoices/{id}/dunnings`, `GET /api/dunnings/{id}/pdf`, `GET /api/dunnings/{id}/email`,
  `POST /api/dunnings/{id}/send`.

## Tests

```bash
cd backend
mvn test
```

## Noch offen

- Eingangsrechnungen mit gemischter Kontierung (verschiedene Aufwandskonten je Position)
- E-Rechnungen per E-Mail empfangen (Postfach-Abruf)
- Verzugszinsen und Mahngebühren im DATEV-Export
- Belege scannen (OCR)

## Produktion (später)

Profil `postgres` aktivieren und Umgebungsvariablen setzen:
`SPRING_PROFILES_ACTIVE=postgres`, `DB_URL`, `DB_USER`, `DB_PASSWORD` und ein eigenes `RECHNIO_JWT_SECRET` (mindestens 32 Zeichen).
