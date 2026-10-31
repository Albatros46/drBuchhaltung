import { File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';

import { ApiError, fetchFile } from './api';

const UTI: Record<string, string> = { 'application/pdf': 'com.adobe.pdf', 'application/xml': 'public.xml' };

/**
 * iOS/Android: Datei in den Cache schreiben und das Teilen-Menue oeffnen
 * (Ansehen, Drucken, per Mail oder Messenger senden, in Dateien sichern).
 */
export async function openDocument(token: string, path: string, fileName: string, mimeType: string): Promise<void> {
  const bytes = new Uint8Array(await (await fetchFile(token, path)).arrayBuffer());
  const file = new File(Paths.cache, fileName);
  if (file.exists) file.delete();
  file.create();
  file.write(bytes);
  if (!(await Sharing.isAvailableAsync())) {
    throw new ApiError('Teilen ist auf diesem Gerät nicht verfügbar', 0);
  }
  await Sharing.shareAsync(file.uri, { mimeType, UTI: UTI[mimeType], dialogTitle: fileName });
}
