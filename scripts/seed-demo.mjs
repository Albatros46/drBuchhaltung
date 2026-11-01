// Fuellt die lokale Datenbank ueber die API mit Beispieldaten fuer das Demo-Konto.
// Alle Firmen, Personen und Adressen sind erfunden; E-Mails enden auf .test bzw. example.de.
//
//   node scripts/seed-demo.mjs            (Backend muss auf http://localhost:8080 laufen)
//   API_URL=http://localhost:8085 node scripts/seed-demo.mjs
//
// Je Tabelle entstehen mindestens 10 Datensaetze: Kunden, Rechnungen mit Positionen, Angebote, Stornos,
// Mahnungen, 10 Lieferanten-Konten (Firma, Benutzer, Logo), deren E-Rechnungen als Eingangsrechnungen
// des Demo-Kontos (Datei, Positionen, Lieferant, Kontierung) und das Audit-Log.
import { deflateSync } from 'node:zlib';

const API = process.env.API_URL ?? 'http://localhost:8080';
const DEMO = { email: 'demo@rechnio.test', password: 'demo-pass-2026', company: 'Demo Fliesenbau' };
const SUPPLIER_PASSWORD = 'demo-pass-2026';
const SEED_MARKER = 'Bäckerei Sonnenschein GmbH';

// --- HTTP ---

async function call(token, method, path, body) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (body instanceof FormData) payload = body;
  else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  const res = await fetch(API + path, { method, headers, body: payload });
  const type = res.headers.get('content-type') ?? '';
  const data = res.status === 204 ? null : type.includes('json') ? await res.json() : Buffer.from(await res.arrayBuffer());
  if (!res.ok) {
    const detail = data && data.message ? `${data.message} ${JSON.stringify(data.errors ?? [])}` : res.status;
    throw new Error(`${method} ${path}: ${detail}`);
  }
  return data;
}

async function account(email, password, company) {
  try {
    return (await call(null, 'POST', '/api/auth/login', { email, password })).token;
  } catch {
    return (await call(null, 'POST', '/api/auth/register', { companyName: company, email, password })).token;
  }
}

function upload(token, path, filename, type, bytes) {
  const form = new FormData();
  form.append('file', new Blob([bytes], { type }), filename);
  return call(token, 'POST', path, form);
}

// --- Logo: einfarbiges PNG mit hellem Balken, ohne Bildbibliothek ---

const CRC_TABLE = Array.from({ length: 256 }, (_, n) => {
  let c = n;
  for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
  return c >>> 0;
});

function crc32(buf) {
  let c = 0xffffffff;
  for (const b of buf) c = CRC_TABLE[(c ^ b) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}

function logoPng([r, g, b]) {
  const w = 300;
  const h = 100;
  const rows = [];
  for (let y = 0; y < h; y++) {
    const row = Buffer.alloc(1 + w * 3);
    for (let x = 0; x < w; x++) {
      const stripe = y > 62 && y < 74 && x > 20 && x < 280;
      row.set(stripe ? [255, 255, 255] : [r, g, b], 1 + x * 3);
    }
    rows.push(row);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr.set([8, 2, 0, 0, 0], 8);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(Buffer.concat(rows))),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// GET liefert 204 ohne Inhalt, wenn noch kein Logo hinterlegt ist
async function ensureLogo(token, rgb) {
  if (!(await call(token, 'GET', '/api/organization/logo'))) {
    await upload(token, '/api/organization/logo', 'logo.png', 'image/png', logoPng(rgb));
  }
}

const supplierEmail = (n) => `lieferant${String(n).padStart(2, '0')}@rechnio.test`;

/** Logos fuer Demo-Firma und Lieferanten; laeuft auch bei bereits vorhandenen Beispieldaten. */
async function ensureLogos(demo) {
  await ensureLogo(demo, [15, 110, 95]);
  for (let n = 1; n <= SUPPLIERS.length; n++) {
    const token = await account(supplierEmail(n), SUPPLIER_PASSWORD, SUPPLIERS[n - 1][0]);
    await ensureLogo(token, [40 + n * 18, 80, 200 - n * 12]);
  }
}

// --- Daten ---

const day = (offset) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return d.toISOString().slice(0, 10);
};

const CUSTOMERS = [
  { name: SEED_MARKER, street: 'Lindenallee 4', zip: '50668', city: 'Köln', email: 'buchhaltung@sonnenschein.example.de', ustId: 'DE100000001' },
  { name: 'Praxis Dr. Weber', street: 'Am Markt 12', zip: '53111', city: 'Bonn', email: 'praxis@weber.example.de' },
  { name: 'Hotel Rheinblick KG', street: 'Uferstraße 7', zip: '56068', city: 'Koblenz', email: 'rechnung@rheinblick.example.de', ustId: 'DE100000003' },
  { name: 'Familie Schneider', street: 'Birkenweg 23', zip: '51465', city: 'Bergisch Gladbach', email: 'schneider@example.de' },
  { name: 'Autohaus Becker GmbH', street: 'Industriestraße 88', zip: '40211', city: 'Düsseldorf', email: 'einkauf@becker.example.de', ustId: 'DE100000005' },
  { name: 'Stadtwerke Musterstadt', street: 'Rathausplatz 1', zip: '45127', city: 'Essen', email: 'eingangsrechnung@stadtwerke.example.de', leitwegId: '05113000-12345-67' },
  { name: 'Café Morgenrot', street: 'Kirchgasse 3', zip: '50667', city: 'Köln', email: 'hallo@morgenrot.example.de' },
  { name: 'Wohnbau Lehmann GmbH', street: 'Hafenstraße 15', zip: '47051', city: 'Duisburg', email: 'kreditoren@lehmann.example.de', ustId: 'DE100000008' },
  { name: 'Kita Regenbogen e. V.', street: 'Schulstraße 9', zip: '42103', city: 'Wuppertal', email: 'vorstand@regenbogen.example.de' },
  { name: 'Ingenieurbüro Hoffmann', street: 'Gartenstraße 31', zip: '44135', city: 'Dortmund', email: 'office@hoffmann.example.de', ustId: 'DE100000010' },
];

const WORK = [
  ['Bodenfliesen verlegen', 'm2', 48.5],
  ['Wandfliesen verlegen', 'm2', 52.0],
  ['Fliesen Feinsteinzeug 60x60', 'm2', 39.9],
  ['Silikonfugen erneuern', 'm', 8.5],
  ['Abdichtung Duschbereich', 'Pauschale', 420.0],
  ['Untergrund ausgleichen', 'm2', 14.0],
  ['Altbelag entfernen und entsorgen', 'm2', 18.0],
  ['Sockelleisten setzen', 'm', 11.5],
  ['Anfahrt', 'Pauschale', 45.0],
  ['Arbeitsstunde Fliesenleger', 'Std', 58.0],
];

function lines(i, count = 3) {
  return Array.from({ length: count }, (_, k) => {
    const [description, unit, unitPrice] = WORK[(i * 3 + k) % WORK.length];
    const quantity = unit === 'Pauschale' ? 1 : 4 + ((i * 7 + k * 5) % 30);
    return { description, quantity, unit, unitPrice, vatRate: 19 };
  });
}

// Lieferanten: Name, Ort, Leistung (Beschreibung, Einheit, Preis, Steuersatz), Kontierung beim Demo-Konto
const SUPPLIERS = [
  ['Fliesenhandel Nord GmbH', 'Hamburg', '20095', ['Feinsteinzeug 30x60 grau', 'm2', 24.9, 19], 'WARENEINGANG'],
  ['Baustoffe Krämer KG', 'Hannover', '30159', ['Fliesenkleber flexibel 25 kg', 'Sack', 18.4, 19], 'WARENEINGANG'],
  ['Büro & Papier Schulz', 'Bremen', '28195', ['Kopierpapier A4, 5 Pakete', 'Stk', 22.5, 19], 'BUEROBEDARF'],
  ['NetzWelt Telekommunikation AG', 'Frankfurt am Main', '60311', ['Mobilfunk und Internet Oktober', 'Monat', 79.9, 19], 'TELEFON_INTERNET'],
  ['Immobilien Vogel GmbH', 'Köln', '50672', ['Miete Lager und Werkstatt', 'Monat', 950, 19], 'MIETE'],
  ['Autowerkstatt Braun', 'Leverkusen', '51373', ['Inspektion Transporter', 'Pauschale', 389, 19], 'KFZ'],
  ['Software Fuchs GmbH', 'München', '80331', ['Lizenz Aufmaß-Software, 1 Jahr', 'Stk', 240, 19], 'SOFTWARE'],
  ['Druckerei Klein', 'Bonn', '53113', ['Flyer A5, 2.000 Stück', 'Pauschale', 189, 19], 'WERBUNG'],
  ['Steuerkanzlei Richter', 'Aachen', '52062', ['Finanzbuchhaltung Oktober', 'Monat', 320, 19], 'BUCHFUEHRUNG'],
  ['Bau-Service Yilmaz', 'Gelsenkirchen', '45879', ['Fliesenarbeiten als Subunternehmer', 'm2', 21, 0], 'REVERSE_CHARGE_BAU'],
];

// --- Ablauf ---

async function main() {
  const demo = await account(DEMO.email, DEMO.password, DEMO.company);
  const existing = await call(demo, 'GET', '/api/customers');
  if (existing.some((c) => c.name === SEED_MARKER)) {
    await ensureLogos(demo);
    console.log('Beispieldaten sind schon vorhanden, nur fehlende Logos ergänzt.');
    return;
  }

  // Firma des Demo-Kontos vervollstaendigen (fehlende Felder), Logo
  const org = await call(demo, 'GET', '/api/organization');
  const demoOrg = {
    ...org,
    street: org.street || 'Fliesenweg 1',
    zip: org.zip || '50667',
    city: org.city || 'Köln',
    steuernummer: org.steuernummer || '214/5678/1234',
    ustId: org.ustId || 'DE123456789',
    kontenrahmen: org.kontenrahmen || 'SKR03',
    iban: org.iban || 'DE02120300000000202051',
    email: org.email || 'info@demo-fliesenbau.example.de',
    contactName: org.contactName || 'Erika Musterfrau',
    phone: org.phone || '+49 221 123456',
    dunningFee: org.dunningFee ?? 5,
  };
  await call(demo, 'PUT', '/api/organization', demoOrg);

  // 10 Kunden
  const customers = [];
  for (const c of CUSTOMERS) customers.push(await call(demo, 'POST', '/api/customers', c));
  console.log(`Kunden: ${customers.length}`);

  const draft = (i, extra = {}) =>
    call(demo, 'POST', '/api/invoices', {
      customerId: customers[i % customers.length].id,
      docType: 'RECHNUNG',
      taxMode: 'STANDARD',
      serviceDateFrom: day(-40 + i),
      lines: lines(i),
      ...extra,
    });
  const issue = (inv) => call(demo, 'POST', `/api/invoices/${inv.id}/issue`);

  // 10 ueberfaellige Rechnungen mit je einer Zahlungserinnerung
  for (let i = 0; i < 10; i++) {
    const inv = await issue(await draft(i, { dueDate: day(-20 - i) }));
    await call(demo, 'POST', `/api/invoices/${inv.id}/dunnings`);
  }
  console.log('Überfällige Rechnungen mit Zahlungserinnerung: 10');

  // 6 bezahlte Rechnungen, eine davon storniert (Storno-Beleg)
  for (let i = 0; i < 6; i++) {
    const inv = await issue(await draft(i + 3));
    if (i === 5) await call(demo, 'POST', `/api/invoices/${inv.id}/cancel`);
    else await call(demo, 'POST', `/api/invoices/${inv.id}/paid`, { paidAt: day(-i) });
  }
  // 2 offene, noch nicht faellige Rechnungen
  for (let i = 0; i < 2; i++) await issue(await draft(i + 6, { dueDate: day(14 + i * 7) }));
  // 4 Angebote, eines davon in eine Rechnung umgewandelt (bleibt Entwurf)
  for (let i = 0; i < 4; i++) {
    const offer = await issue(await draft(i + 1, { docType: 'ANGEBOT', introText: 'Gerne bieten wir Ihnen an:' }));
    if (i === 0) await call(demo, 'POST', `/api/invoices/${offer.id}/convert`);
  }
  // 2 Entwuerfe
  for (let i = 0; i < 2; i++) await draft(i + 8);
  console.log('Weitere Belege: 6 bezahlt/storniert, 2 offen, 4 Angebote, 3 Entwürfe');

  // 10 Lieferanten mit eigenem Konto: stellen dem Demo-Konto eine XRechnung aus, die dort eingelesen wird
  const demoAsCustomer = {
    name: demoOrg.name,
    street: demoOrg.street,
    zip: demoOrg.zip,
    city: demoOrg.city,
    email: demoOrg.email,
    ustId: demoOrg.ustId,
  };
  let n = 0;
  for (const [name, city, zip, [description, unit, unitPrice, vatRate], category] of SUPPLIERS) {
    n++;
    const nr = String(n).padStart(2, '0');
    const token = await account(supplierEmail(n), SUPPLIER_PASSWORD, name);
    await call(token, 'PUT', '/api/organization', {
      name,
      street: `Handelsweg ${n * 3}`,
      zip,
      city,
      steuernummer: `${100 + n}/200/300${nr}`,
      ustId: `DE2000000${nr}`,
      kleinunternehmer: false,
      kontenrahmen: 'SKR03',
      iban: 'DE02120300000000202051',
      email: `rechnung@lieferant${nr}.example.de`,
      contactName: 'Max Mustermann',
      phone: `+49 30 55500${nr}`,
    });
    const buyer = await call(token, 'POST', '/api/customers', demoAsCustomer);
    const quantity = unit === 'Monat' || unit === 'Pauschale' ? 1 : 10 + n * 4;
    const out = await call(token, 'POST', '/api/invoices', {
      customerId: buyer.id,
      docType: 'RECHNUNG',
      taxMode: vatRate === 0 ? 'REVERSE_CHARGE' : 'STANDARD',
      serviceDateFrom: day(-30 + n),
      lines: [
        { description, quantity, unit, unitPrice, vatRate },
        { description: 'Versandkosten', quantity: 1, unit: 'Pauschale', unitPrice: 9.9, vatRate },
      ],
    });
    await call(token, 'POST', `/api/invoices/${out.id}/issue`);
    // abwechselnd XRechnung (XML) und ZUGFeRD (PDF)
    const pdf = n % 2 === 0;
    const file = await call(token, 'GET', `/api/invoices/${out.id}/${pdf ? 'zugferd' : 'xrechnung'}`);
    const incoming = await upload(
      demo,
      '/api/incoming-invoices',
      `${name.replace(/[^A-Za-z0-9]+/g, '_')}.${pdf ? 'pdf' : 'xml'}`,
      pdf ? 'application/pdf' : 'application/xml',
      file,
    );
    if (category !== incoming.expense.category) {
      await call(demo, 'PUT', `/api/incoming-invoices/${incoming.id}/expense`, { category, rememberForSupplier: true });
    }
    if (n <= 4) await call(demo, 'POST', `/api/incoming-invoices/${incoming.id}/paid`, { paidAt: day(-n) });
  }
  console.log(`Lieferanten und Eingangsrechnungen: ${n}`);
  await ensureLogos(demo);
  console.log('Logos: Demo-Firma und alle Lieferanten');
  console.log(`Fertig. Anmelden in der App mit ${DEMO.email}.`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
