import { InvoiceStatus, LineInput } from './api';

const eur = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });

export const money = (v: number) => eur.format(v);

/** Betrag fuer ein Eingabefeld: 48.5 -> "48,50". */
export const priceInput = (v: number) => v.toFixed(2).replace('.', ',');

/** ISO-Datum (2026-10-31) als 31.10.2026. */
export const date = (iso: string | null) => (iso ? iso.split('-').reverse().join('.') : '–');

export const today = () => new Date().toISOString().slice(0, 10);

/** Deutsche Eingabe "1.250,50" oder "85.50" als Zahl; leer oder ungueltig ergibt NaN. */
export function parseDecimal(s: string): number {
  const t = s.trim();
  if (!t) return NaN;
  const normalized = t.includes(',') ? t.replace(/\./g, '').replace(',', '.') : t;
  const v = Number(normalized);
  return Number.isFinite(v) ? v : NaN;
}

export const statusLabel: Record<InvoiceStatus, string> = {
  DRAFT: 'Entwurf',
  ISSUED: 'offen',
  PAID: 'bezahlt',
  CANCELLED: 'storniert',
};

const round2 = (v: number) => Math.round((v + Number.EPSILON) * 100) / 100;

/** Vorschau der Summen; das Backend rechnet verbindlich (je Steuersatz gerundet). */
export function previewTotals(lines: LineInput[]) {
  const byRate = new Map<number, number>();
  for (const l of lines) {
    if (!Number.isFinite(l.quantity) || !Number.isFinite(l.unitPrice)) continue;
    byRate.set(l.vatRate, (byRate.get(l.vatRate) ?? 0) + round2(l.quantity * l.unitPrice));
  }
  let net = 0;
  let vat = 0;
  for (const [rate, n] of byRate) {
    net += n;
    vat += round2((n * rate) / 100);
  }
  return { net: round2(net), vat: round2(vat), gross: round2(net + vat) };
}
