import { useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { ActionButton, AmountRow, ErrorBox, Screen } from '@/components/app-ui';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Text } from '@/components/ui/text';
import { api, ApiError, IncomingInvoice } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { openDocument } from '@/lib/download';
import { date, money } from '@/lib/format';
import { formatLabel } from '@/lib/incoming';

export default function IncomingInvoiceScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const token = useToken();
  const [inv, setInv] = useState<IncomingInvoice | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(() => {
    api.incomingInvoice(token, id).then(setInv).catch((e: ApiError) => setError(e));
  }, [token, id]);
  useEffect(load, [load]);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      const result = await action();
      if (result && typeof result === 'object' && 'id' in result) setInv(result as IncomingInvoice);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  if (!inv) {
    return (
      <Screen>
        <ErrorBox message={error?.message ?? null} />
        {!error && <ActivityIndicator />}
      </Screen>
    );
  }

  const address = [inv.supplierStreet, [inv.supplierZip, inv.supplierCity].filter(Boolean).join(' ')]
    .filter(Boolean)
    .join(', ');
  const isPdf = inv.format === 'ZUGFERD';

  return (
    <Screen>
      <Card>
        <CardHeader>
          <View className="flex-row items-center justify-between gap-2">
            <CardTitle className="flex-1">{inv.supplierName ?? 'Unbekannter Lieferant'}</CardTitle>
            <Badge variant={inv.status === 'PAID' ? 'default' : 'outline'}>
              <Text>{inv.status === 'PAID' ? `bezahlt ${date(inv.paidAt)}` : 'offen'}</Text>
            </Badge>
          </View>
          {address ? <CardDescription>{address}</CardDescription> : null}
        </CardHeader>
        <CardContent className="gap-1">
          <Info label={inv.documentType === 'GUTSCHRIFT' ? 'Gutschrift-Nr.' : 'Rechnungs-Nr.'} value={inv.number} />
          <Info label="Rechnungsdatum" value={date(inv.issueDate)} />
          <Info label="Fällig am" value={inv.dueDate ? date(inv.dueDate) : null} />
          <Info label="USt-IdNr." value={inv.supplierVatId} />
          <Info label="Steuernummer" value={inv.supplierTaxId} />
          <Info label="IBAN" value={inv.supplierIban} selectable />
          <Info label="Ihre Referenz" value={inv.buyerReference} />
          <Info label="Format" value={formatLabel[inv.format]} />
          <Info label="DATEV-Kreditor" value={inv.kreditorAccount != null ? String(inv.kreditorAccount) : null} />
          {inv.paymentTerms ? <Text variant="muted" className="mt-2">{inv.paymentTerms}</Text> : null}
        </CardContent>
      </Card>

      <ValidationCard inv={inv} />

      <Card>
        <CardHeader>
          <CardTitle>Positionen</CardTitle>
        </CardHeader>
        <CardContent className="gap-3">
          {inv.lines.map((l) => (
            <View key={l.position} className="flex-row items-start justify-between gap-3">
              <View className="flex-1">
                <Text className="font-semibold">{l.description ?? '–'}</Text>
                <Text variant="muted">
                  {l.quantity ?? '–'} {l.unit ?? ''} × {l.unitPrice != null ? money(l.unitPrice) : '–'}
                  {l.vatRate != null ? ` · ${l.vatRate} %` : ''}
                </Text>
              </View>
              <Text className="tabular-nums">{l.netAmount != null ? money(l.netAmount) : '–'}</Text>
            </View>
          ))}
          <Separator />
          <AmountRow label="Netto" value={money(inv.netTotal)} />
          <AmountRow label="USt" value={money(inv.vatTotal)} />
          <AmountRow label="Gesamt" value={money(inv.grossTotal)} strong />
        </CardContent>
      </Card>

      <ErrorBox message={error?.message ?? null} errors={error?.errors} />

      {inv.status === 'OPEN' && (
        <ActionButton
          title={inv.documentType === 'GUTSCHRIFT' ? 'Als verrechnet markieren' : 'Als bezahlt markieren'}
          busy={busy}
          onPress={() => run(() => api.markIncomingPaid(token, inv.id))}
        />
      )}
      <ActionButton
        title={isPdf ? 'Original-PDF öffnen' : 'Original-XML herunterladen'}
        variant="outline"
        disabled={busy}
        onPress={() =>
          run(() =>
            openDocument(
              token,
              `/api/incoming-invoices/${inv.id}/original`,
              inv.originalFilename,
              isPdf ? 'application/pdf' : 'application/xml',
            ),
          )
        }
      />
      <Text variant="muted">Eingegangen am {new Date(inv.receivedAt).toLocaleString('de-DE')} · {inv.originalFilename}</Text>
    </Screen>
  );
}

function Info({ label, value, selectable }: { label: string; value: string | null; selectable?: boolean }) {
  if (!value) return null;
  return (
    <View className="flex-row justify-between gap-3">
      <Text variant="muted">{label}</Text>
      <Text className="flex-1 text-right" selectable={selectable}>
        {value}
      </Text>
    </View>
  );
}

function ValidationCard({ inv }: { inv: IncomingInvoice }) {
  if (inv.validationStatus === 'VALID') {
    return (
      <View className="rounded-md border border-primary/40 bg-secondary p-3">
        <Text className="font-semibold text-secondary-foreground">Prüfung bestanden</Text>
        <Text variant="muted">Die Datei entspricht EN 16931{inv.format.startsWith('XRECHNUNG') ? ' und XRechnung' : ''}.</Text>
      </View>
    );
  }
  return (
    <View className="gap-1 rounded-md border border-destructive/40 bg-destructive/10 p-3">
      <Text className="font-semibold text-destructive">Prüfung: Fehler gefunden</Text>
      <Text variant="muted">
        Die Rechnung wurde eingelesen, ist aber formal nicht korrekt. Bitten Sie den Lieferanten um eine berichtigte Rechnung.
      </Text>
      {inv.validationMessages.map((m) => (
        <Text key={m} className="text-destructive">
          • {m}
        </Text>
      ))}
    </View>
  );
}
