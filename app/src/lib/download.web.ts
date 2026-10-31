import { fetchFile } from './api';

/**
 * Web: PDFs in einem neuen Tab anzeigen, andere Dateien (XML) herunterladen.
 * Der Tab wird noch im Klick geoeffnet, sonst blockiert der Popup-Blocker ihn nach dem Laden.
 */
export async function openDocument(token: string, path: string, fileName: string, mimeType: string): Promise<void> {
  const win = mimeType === 'application/pdf' ? window.open('', '_blank') : null;
  let blob: Blob;
  try {
    blob = await (await fetchFile(token, path)).blob();
  } catch (e) {
    win?.close();
    throw e;
  }
  const url = URL.createObjectURL(blob);
  if (win) {
    win.location.href = url;
  } else {
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
