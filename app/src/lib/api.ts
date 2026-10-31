import { Platform } from 'react-native';

// Lokal: Web und iOS-Simulator erreichen den Rechner ueber localhost, der Android-Emulator ueber 10.0.2.2.
// Fuer ein echtes Handy EXPO_PUBLIC_API_URL auf die LAN-IP setzen, z. B. http://192.168.0.10:8080
const DEFAULT_URL = Platform.OS === 'android' ? 'http://10.0.2.2:8080' : 'http://localhost:8080';
export const API_URL = process.env.EXPO_PUBLIC_API_URL ?? DEFAULT_URL;

export type DocType = 'ANGEBOT' | 'RECHNUNG' | 'STORNO';
export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PAID' | 'CANCELLED';
export type TaxMode = 'STANDARD' | 'KLEINUNTERNEHMER' | 'REVERSE_CHARGE';

export interface TokenResponse {
  token: string;
  email: string;
  companyName: string;
}

export interface Organization {
  name: string;
  street?: string | null;
  zip?: string | null;
  city?: string | null;
  country?: string | null;
  steuernummer?: string | null;
  ustId?: string | null;
  kleinunternehmer: boolean;
  kontenrahmen: 'SKR03' | 'SKR04';
  iban?: string | null;
  bic?: string | null;
  email?: string | null;
  contactName?: string | null;
  phone?: string | null;
  datevBeraterNr?: number | null;
  datevMandantNr?: number | null;
}

export interface Customer {
  id: string;
  customerNumber: number;
  name: string;
  street?: string | null;
  zip?: string | null;
  city?: string | null;
  email?: string | null;
  ustId?: string | null;
}

export interface InvoiceLine {
  position: number;
  description: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  vatRate: number;
  netAmount: number;
}

export interface Invoice {
  id: string;
  customerId: string;
  docType: DocType;
  status: InvoiceStatus;
  taxMode: TaxMode;
  number: string | null;
  issueDate: string | null;
  serviceDateFrom: string | null;
  dueDate: string | null;
  netTotal: number;
  vatTotal: number;
  grossTotal: number;
  paidAt: string | null;
  locked: boolean;
  legalNote: string | null;
  lines: InvoiceLine[];
}

export interface LineInput {
  description: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  vatRate: number;
}

export interface DraftInput {
  customerId: string;
  docType: DocType;
  serviceDateFrom: string | null;
  lines: LineInput[];
}

/** Fehler vom Backend, inklusive der §14-UStG-Pruefliste bei HTTP 422. */
export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errors: string[] = [],
  ) {
    super(message);
  }
}

async function request<T>(path: string, token: string | null, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, { ...init, headers });
  } catch {
    throw new ApiError(`Server nicht erreichbar (${API_URL}). Läuft das Backend?`, 0);
  }
  if (res.status === 204) return undefined as T;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new ApiError(body.message ?? `Fehler ${res.status}`, res.status, body.errors ?? []);
  }
  return body as T;
}

const json = (data: unknown) => JSON.stringify(data);

export const api = {
  register: (companyName: string, email: string, password: string) =>
    request<TokenResponse>('/api/auth/register', null, {
      method: 'POST',
      body: json({ companyName, email, password }),
    }),
  login: (email: string, password: string) =>
    request<TokenResponse>('/api/auth/login', null, { method: 'POST', body: json({ email, password }) }),

  organization: (t: string) => request<Organization>('/api/organization', t),
  saveOrganization: (t: string, org: Organization) =>
    request<Organization>('/api/organization', t, { method: 'PUT', body: json(org) }),

  customers: (t: string) => request<Customer[]>('/api/customers', t),
  createCustomer: (t: string, c: Omit<Customer, 'id' | 'customerNumber'>) =>
    request<Customer>('/api/customers', t, { method: 'POST', body: json(c) }),

  invoices: (t: string) => request<Invoice[]>('/api/invoices', t),
  invoice: (t: string, id: string) => request<Invoice>(`/api/invoices/${id}`, t),
  createInvoice: (t: string, d: DraftInput) =>
    request<Invoice>('/api/invoices', t, { method: 'POST', body: json(d) }),
  issue: (t: string, id: string) => request<Invoice>(`/api/invoices/${id}/issue`, t, { method: 'POST' }),
  markPaid: (t: string, id: string) => request<Invoice>(`/api/invoices/${id}/paid`, t, { method: 'POST' }),
  cancel: (t: string, id: string) => request<Invoice>(`/api/invoices/${id}/cancel`, t, { method: 'POST' }),
  deleteDraft: (t: string, id: string) => request<void>(`/api/invoices/${id}`, t, { method: 'DELETE' }),
};
