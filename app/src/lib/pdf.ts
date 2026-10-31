import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { API_URL, ApiError } from './api';

/**
 * iOS/Android: PDF in den Cache laden und das Teilen-Menue oeffnen
 * (Ansehen, Drucken, per Mail oder Messenger senden, in Dateien sichern).
 */
export async function openInvoicePdf(token: string, invoiceId: string, fileName: string): Promise<void> {
  const target = new File(Paths.cache, fileName);
  if (target.exists) target.delete();
  const task = File.createDownloadTask(`${API_URL}/api/invoices/${invoiceId}/pdf`, target, {
    headers: { Authorization: `Bearer ${token}` },
  });
  let file: File | null;
  try {
    file = await task.downloadAsync();
  } catch {
    throw new ApiError(`PDF konnte nicht geladen werden (${API_URL}). Läuft das Backend?`, 0);
  }
  if (!file) throw new ApiError('PDF-Download abgebrochen', 0);
  if (!(await Sharing.isAvailableAsync())) {
    throw new ApiError('Teilen ist auf diesem Gerät nicht verfügbar', 0);
  }
  await Sharing.shareAsync(file.uri, { mimeType: 'application/pdf', UTI: 'com.adobe.pdf', dialogTitle: fileName });
}
