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
  /** Mahngebuehr ab der 1. Mahnung */
  dunningFee?: number | null;
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
  leitwegId?: string | null;
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
  sentAt: string | null;
  sentTo: string | null;
  lines: InvoiceLine[];
}

export type MailAttachment = 'ZUGFERD' | 'PDF' | 'XRECHNUNG';

export type DunningLevel = 'ZAHLUNGSERINNERUNG' | 'MAHNUNG_1' | 'MAHNUNG_2';

export interface Dunning {
  id: string;
  invoiceId: string;
  level: DunningLevel;
  title: string;
  issueDate: string;
  deadline: string;
  openAmount: number;
  fee: number;
  totalFees: number;
  totalDue: number;
  sentAt: string | null;
  sentTo: string | null;
}

/** E-Mail-Entwurf; attachment nur bei Rechnungen (Mahnungen gehen immer als PDF). */
export interface MailDraft {
  to: string;
  subject: string;
  message: string;
  attachment?: MailAttachment;
}

export interface InvoiceMail {
  to: string;
  subject: string;
  message: string;
  attachment: MailAttachment;
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

export type EInvoiceFormat = 'ZUGFERD' | 'XRECHNUNG_CII' | 'XRECHNUNG_UBL' | 'CII' | 'UBL';

export type ExpenseCategory = string;

/** Waehlbare Kontierung mit den Konten im Kontenrahmen der Firma. */
export interface ExpenseCategoryOption {
  code: ExpenseCategory;
  label: string;
  accounts: string;
  taxNote: string;
}

export interface ExpenseInput {
  category: ExpenseCategory;
  account?: number;
  buKey?: string;
  rememberForSupplier: boolean;
}

export interface IncomingInvoice {
  id: string;
  format: EInvoiceFormat;
  documentType: 'RECHNUNG' | 'GUTSCHRIFT';
  status: 'OPEN' | 'PAID';
  supplierName: string | null;
  supplierStreet: string | null;
  supplierZip: string | null;
  supplierCity: string | null;
  supplierVatId: string | null;
  supplierTaxId: string | null;
  supplierIban: string | null;
  number: string | null;
  issueDate: string | null;
  dueDate: string | null;
  currency: string | null;
  buyerReference: string | null;
  paymentTerms: string | null;
  netTotal: number;
  vatTotal: number;
  grossTotal: number;
  validationStatus: 'VALID' | 'INVALID';
  validationMessages: string[];
  originalFilename: string;
  paidAt: string | null;
  receivedAt: string;
  /** DATEV-Kreditorenkonto des Lieferanten (70000 + Lieferantennummer) */
  kreditorAccount: number | null;
  /** Kontierung fuer den DATEV-Export; chosen = false heisst Vorschlag */
  expense: {
    category: ExpenseCategory;
    label: string;
    accounts: string;
    buKey: string | null;
    chosen: boolean;
    reverseCharge: boolean;
  };
  lines: {
    position: number;
    description: string | null;
    quantity: number | null;
    /** lesbare Einheit, z. B. "m²" */
    unit: string | null;
    /** UN/ECE-Code aus der E-Rechnung, z. B. "MTK" */
    unitCode: string | null;
    unitPrice: number | null;
    vatRate: number | null;
    netAmount: number | null;
  }[];
}

/** Firmenlogo; dataUri kann direkt als Bildquelle verwendet werden. */
export interface Logo {
  dataUri: string;
  width: number;
  height: number;
}

/** Datei aus dem Dokumenten-Picker: auf dem Handy per URI, im Browser als File-Objekt. */
export interface PickedFile {
  uri: string;
  name: string;
  mimeType?: string;
  file?: File;
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

/** Laedt eine erzeugte Datei (PDF, XML). Bei Fehlern kommt die Begruendung des Backends inklusive Liste. */
export async function fetchFile(token: string, path: string): Promise<Response> {
  let res: Response;
  try {
    res = await fetch(`${API_URL}${path}`, { headers: { Authorization: `Bearer ${token}` } });
  } catch {
    throw new ApiError(`Server nicht erreichbar (${API_URL}). Läuft das Backend?`, 0);
  }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new ApiError(body.message ?? `Datei konnte nicht erzeugt werden (Fehler ${res.status})`, res.status, body.errors ?? []);
  }
  return res;
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
  updateCustomer: (t: string, id: string, c: Omit<Customer, 'id' | 'customerNumber'>) =>
    request<Customer>(`/api/customers/${id}`, t, { method: 'PUT', body: json(c) }),
  deleteCustomer: (t: string, id: string) => request<void>(`/api/customers/${id}`, t, { method: 'DELETE' }),

  invoices: (t: string) => request<Invoice[]>('/api/invoices', t),
  invoice: (t: string, id: string) => request<Invoice>(`/api/invoices/${id}`, t),
  createInvoice: (t: string, d: DraftInput) =>
    request<Invoice>('/api/invoices', t, { method: 'POST', body: json(d) }),
  issue: (t: string, id: string) => request<Invoice>(`/api/invoices/${id}/issue`, t, { method: 'POST' }),
  markPaid: (t: string, id: string) => request<Invoice>(`/api/invoices/${id}/paid`, t, { method: 'POST' }),
  cancel: (t: string, id: string) => request<Invoice>(`/api/invoices/${id}/cancel`, t, { method: 'POST' }),
  deleteDraft: (t: string, id: string) => request<void>(`/api/invoices/${id}`, t, { method: 'DELETE' }),
  emailDraft: (t: string, id: string) => request<InvoiceMail>(`/api/invoices/${id}/email`, t),
  sendInvoice: (t: string, id: string, mail: InvoiceMail) =>
    request<Invoice>(`/api/invoices/${id}/send`, t, { method: 'POST', body: json(mail) }),

  dunnings: (t: string, invoiceId: string) => request<Dunning[]>(`/api/invoices/${invoiceId}/dunnings`, t),
  createDunning: (t: string, invoiceId: string) =>
    request<Dunning>(`/api/invoices/${invoiceId}/dunnings`, t, { method: 'POST' }),
  dunningEmailDraft: (t: string, id: string) => request<MailDraft>(`/api/dunnings/${id}/email`, t),
  sendDunning: (t: string, id: string, mail: MailDraft) =>
    request<Dunning>(`/api/dunnings/${id}/send`, t, { method: 'POST', body: json(mail) }),

  incomingInvoices: (t: string) => request<IncomingInvoice[]>('/api/incoming-invoices', t),
  incomingInvoice: (t: string, id: string) => request<IncomingInvoice>(`/api/incoming-invoices/${id}`, t),
  markIncomingPaid: (t: string, id: string) =>
    request<IncomingInvoice>(`/api/incoming-invoices/${id}/paid`, t, { method: 'POST' }),
  expenseCategories: (t: string) =>
    request<ExpenseCategoryOption[]>('/api/incoming-invoices/expense-categories', t),
  assignExpense: (t: string, id: string, e: ExpenseInput) =>
    request<IncomingInvoice>(`/api/incoming-invoices/${id}/expense`, t, { method: 'PUT', body: JSON.stringify(e) }),
  uploadIncoming: (t: string, f: PickedFile) => uploadFile<IncomingInvoice>(t, '/api/incoming-invoices', f),

  logo: async (t: string): Promise<Logo | null> => (await request<Logo | undefined>('/api/organization/logo', t)) ?? null,
  uploadLogo: (t: string, f: PickedFile) => uploadFile<Logo>(t, '/api/organization/logo', f),
  deleteLogo: (t: string) => request<void>('/api/organization/logo', t, { method: 'DELETE' }),
};

/** Laedt eine ausgewaehlte Datei als multipart/form-data (Feld "file") hoch. */
async function uploadFile<T>(token: string, path: string, picked: PickedFile): Promise<T> {
  const form = new FormData();
  if (Platform.OS === 'web' && picked.file) {
    form.append('file', picked.file, picked.name);
  } else {
    // React Native: Datei wird ueber ihre URI gestreamt
    form.append('file', { uri: picked.uri, name: picked.name, type: picked.mimeType ?? 'application/octet-stream' } as unknown as Blob);
  }
  let res: Response;
  try {
    // Content-Type nicht setzen: fetch ergaenzt die multipart-Boundary selbst
    res = await fetch(`${API_URL}${path}`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form,
    });
  } catch {
    throw new ApiError(`Server nicht erreichbar (${API_URL}). Läuft das Backend?`, 0);
  }
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new ApiError(body.message ?? `Fehler ${res.status}`, res.status, body.errors ?? []);
  return body as T;
}
