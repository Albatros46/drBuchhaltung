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

Erster Ablauf in der App: Konto erstellen → Firma und Steuer ausfüllen (Anschrift, Steuernummer) → Kunde anlegen → Neue Rechnung → Ausstellen.

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
| GET/POST/PUT | `/api/customers` | Kunden |
| GET/POST/PUT/DELETE | `/api/invoices` | Entwürfe von Rechnungen und Angeboten |
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
| GET | `/api/exports/datev?from=2026-01-01&to=2026-12-31` | DATEV-Buchungsstapel (EXTF CSV) |

## Fachliche Regeln

- Pflichtangaben nach § 14 UStG werden vor dem Ausstellen geprüft (HTTP 422 mit Liste der fehlenden Angaben).
- Lückenlose Nummernkreise je Jahr und Belegart: `RE-2026-0001`, `AN-…`, `ST-…`.
- Ausgestellte Belege sind unveränderlich; Korrekturen nur per Storno (GoBD). Änderungen stehen im `audit_log`.
- § 19 (Kleinunternehmer) und § 13b (Reverse-Charge) setzen den Steuersatz auf 0 % und liefern den Pflichthinweis.
- DATEV-Export: Debitor `10000 + Kundennummer` an Erlös-Automatikkonto (SKR03 8400/8300, SKR04 4400/4300).
  **Vor dem produktiven Einsatz einmal mit dem Steuerberater probeweise in DATEV importieren.**

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

## Tests

```bash
cd backend
mvn test
```

## Noch offen

- Logo im PDF-Briefkopf
- Eingangsrechnungen im DATEV-Export (Kreditoren)
- E-Rechnungen per E-Mail empfangen (Postfach-Abruf)
- Versand per E-Mail, Mahnungen
- Belege scannen (OCR)

## Produktion (später)

Profil `postgres` aktivieren und Umgebungsvariablen setzen:
`SPRING_PROFILES_ACTIVE=postgres`, `DB_URL`, `DB_USER`, `DB_PASSWORD` und ein eigenes `RECHNIO_JWT_SECRET` (mindestens 32 Zeichen).
