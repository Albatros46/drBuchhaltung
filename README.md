# Rechnio (drBuchhaltung)

Rechnungen, Angebote, Stornos und DATEV-Export für kleine Unternehmen in Deutschland.
Ein Spring-Boot-Backend und eine Expo-App (iOS, Android und Web aus einem Code).

```
Rechnio/
├─ backend/   Spring Boot 3.5 · Java 21 · H2 (lokal) / PostgreSQL (Produktion) · JWT
└─ app/       Expo SDK 58 · Expo Router · TypeScript
```

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
| POST | `/api/invoices/{id}/issue` | Ausstellen: Nummer vergeben, festschreiben |
| POST | `/api/invoices/{id}/paid` | Als bezahlt markieren |
| POST | `/api/invoices/{id}/cancel` | Stornorechnung erzeugen |
| POST | `/api/invoices/{id}/convert` | Angebot in Rechnung umwandeln |
| GET | `/api/exports/datev?from=2026-01-01&to=2026-12-31` | DATEV-Buchungsstapel (EXTF CSV) |

## Fachliche Regeln

- Pflichtangaben nach § 14 UStG werden vor dem Ausstellen geprüft (HTTP 422 mit Liste der fehlenden Angaben).
- Lückenlose Nummernkreise je Jahr und Belegart: `RE-2026-0001`, `AN-…`, `ST-…`.
- Ausgestellte Belege sind unveränderlich; Korrekturen nur per Storno (GoBD). Änderungen stehen im `audit_log`.
- § 19 (Kleinunternehmer) und § 13b (Reverse-Charge) setzen den Steuersatz auf 0 % und liefern den Pflichthinweis.
- DATEV-Export: Debitor `10000 + Kundennummer` an Erlös-Automatikkonto (SKR03 8400/8300, SKR04 4400/4300).
  **Vor dem produktiven Einsatz einmal mit dem Steuerberater probeweise in DATEV importieren.**

## Tests

```bash
cd backend
mvn test
```

## Noch offen

- PDF-Ausgabe der Rechnung und E-Rechnung (XRechnung / ZUGFeRD)
- Versand per E-Mail, Mahnungen
- Belege scannen (OCR)

## Produktion (später)

Profil `postgres` aktivieren und Umgebungsvariablen setzen:
`SPRING_PROFILES_ACTIVE=postgres`, `DB_URL`, `DB_USER`, `DB_PASSWORD` und ein eigenes `RECHNIO_JWT_SECRET` (mindestens 32 Zeichen).
