import { useState } from 'react';
import { View } from 'react-native';

import { ActionButton, Chip, ErrorBox, Screen } from '@/components/app-ui';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ApiError } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { openDocument } from '@/lib/download';

/** Alle Downloads fuer Steuerberater und Weiterverarbeitung an einer Stelle. */
export default function ExportScreen() {
  const token = useToken();
  const year = new Date().getFullYear();
  const [exportYear, setExportYear] = useState(year);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const download = async (path: string, filename: string) => {
    setBusy(true);
    setError(null);
    try {
      await openDocument(token, path, filename, 'text/csv');
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <Card>
        <CardHeader>
          <CardTitle>DATEV</CardTitle>
          <CardDescription>
            Buchungsstapel mit Ausgangs- und Eingangsrechnungen für Ihren Steuerberater. Die Stammdaten legen die
            Personenkonten mit Name, Anschrift, USt-IdNr. und IBAN an; zuerst importieren. Kontenrahmen sowie Berater-
            und Mandantennummer stehen in den Firmendaten.
          </CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          <View className="flex-row gap-2">
            {[year - 1, year].map((y) => (
              <Chip key={y} label={String(y)} active={exportYear === y} onPress={() => setExportYear(y)} />
            ))}
          </View>
          <ActionButton
            title={`Buchungsstapel ${exportYear} herunterladen`}
            variant="outline"
            disabled={busy}
            onPress={() =>
              download(
                `/api/exports/datev?from=${exportYear}-01-01&to=${exportYear}-12-31`,
                `EXTF_Buchungsstapel_${exportYear}.csv`,
              )
            }
          />
          <ActionButton
            title="Stammdaten (Debitoren/Kreditoren) herunterladen"
            variant="outline"
            disabled={busy}
            onPress={() => download('/api/exports/datev/stammdaten', 'EXTF_Debitoren_Kreditoren.csv')}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Listen</CardTitle>
          <CardDescription>CSV-Dateien für Excel oder andere Programme.</CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          <ActionButton
            title="Kunden (CSV)"
            variant="outline"
            disabled={busy}
            onPress={() => download('/api/customers/export', 'Kunden.csv')}
          />
        </CardContent>
      </Card>

      <ErrorBox message={error?.message ?? null} errors={error?.errors} />
    </Screen>
  );
}
