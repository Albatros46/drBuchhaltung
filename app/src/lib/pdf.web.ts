import { API_URL, ApiError } from './api';

/**
 * Web: PDF mit Token laden und in einem neuen Tab anzeigen. Der Tab wird noch im Klick
 * geoeffnet, sonst blockiert der Popup-Blocker ihn nach dem asynchronen Laden.
 */
export async function openInvoicePdf(token: string, invoiceId: string, fileName: string): Promise<void> {
  const win = window.open('', '_blank');
  let res: Response;
  try {
    res = await fetch(`${API_URL}/api/invoices/${invoiceId}/pdf`, {
      headers: { Authorization: `Bearer ${token}` },
    });
  } catch {
    win?.close();
    throw new ApiError(`Server nicht erreichbar (${API_URL}). Läuft das Backend?`, 0);
  }
  if (!res.ok) {
    win?.close();
    throw new ApiError(`PDF konnte nicht erzeugt werden (Fehler ${res.status})`, res.status);
  }
  const url = URL.createObjectURL(await res.blob());
  if (win) {
    win.location.href = url;
  } else {
    // Popup blockiert: als Datei herunterladen
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
