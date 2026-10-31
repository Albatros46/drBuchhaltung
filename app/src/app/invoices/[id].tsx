import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, View } from 'react-native';

import { ActionButton, AmountRow, ErrorBox, Screen, StatusPill } from '@/components/app-ui';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Text } from '@/components/ui/text';
import { api, ApiError, Invoice } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { date, money } from '@/lib/format';
import { openDocument } from '@/lib/download';

const DOC_TITLE = { RECHNUNG: 'Rechnung', ANGEBOT: 'Angebot', STORNO: 'Stornorechnung' } as const;

function fileName(inv: Invoice, kind: 'pdf' | 'zugferd' | 'xrechnung') {
  const base = inv.number ? `${DOC_TITLE[inv.docType]}-${inv.number}` : `Entwurf-${DOC_TITLE[inv.docType]}`;
  if (kind === 'zugferd') return `${base}-ZUGFeRD.pdf`;
  if (kind === 'xrechnung') return `XRechnung-${inv.number}.xml`;
  return `${base}.pdf`;
}

export default function InvoiceDetailScreen() {
  const { id, issue } = useLocalSearchParams<{ id: string; issue?: string }>();
  const token = useToken();
  const router = useRouter();
  const [inv, setInv] = useState<Invoice | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);

  const load = useCallback(() => {
    api.invoice(token, id).then(setInv).catch((e: ApiError) => setError(e));
  }, [token, id]);

  useEffect(() => {
    if (issue === '1') {
      // Von "Neue Rechnung → Ausstellen": direkt ausstellen, fehlende Pflichtangaben werden hier angezeigt.
      router.setParams({ issue: undefined });
      api.issue(token, id).then(setInv).catch((e: ApiError) => {
        setError(e);
        load();
      });
    } else {
      load();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- nur beim Oeffnen der Seite ausfuehren
  }, []);

  const run = async (action: () => Promise<Invoice | void>, after?: (r: Invoice | void) => void) => {
    setBusy(true);
    setError(null);
    try {
      const result = await action();
      if (after) after(result);
      else load();
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
      setConfirmCancel(false);
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

  const title = inv.number ?? `Entwurf (${inv.docType === 'ANGEBOT' ? 'Angebot' : 'Rechnung'})`;

  return (
    <Screen>
      <Card>
        <CardHeader>
          <View className="flex-row items-center justify-between">
            <CardTitle>{title}</CardTitle>
            <StatusPill status={inv.status} />
          </View>
          <CardDescription>
            Ausgestellt: {date(inv.issueDate)} · Leistung: {date(inv.serviceDateFrom)} · Fällig: {date(inv.dueDate)}
          </CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          {inv.lines.map((l) => (
            <View key={l.position} className="flex-row items-start justify-between gap-3">
              <View className="flex-1">
                <Text className="font-semibold">{l.description}</Text>
                <Text variant="muted">
                  {l.quantity} {l.unit} × {money(l.unitPrice)} · {l.vatRate} %
                </Text>
              </View>
              <Text className="tabular-nums">{money(l.netAmount)}</Text>
            </View>
          ))}
          <Separator />
          <AmountRow label="Netto" value={money(inv.netTotal)} />
          <AmountRow label="USt" value={money(inv.vatTotal)} />
          <AmountRow label="Gesamt" value={money(inv.grossTotal)} strong />
          {inv.legalNote && <Text variant="muted">{inv.legalNote}</Text>}
        </CardContent>
      </Card>

      <ErrorBox message={error?.message ?? null} errors={error?.errors} />

      <ActionButton
        title={inv.status === 'DRAFT' ? 'PDF-Vorschau (Entwurf)' : 'PDF öffnen / teilen'}
        variant="secondary"
        disabled={busy}
        onPress={() =>
          run(() => openDocument(token, `/api/invoices/${inv.id}/pdf`, fileName(inv, 'pdf'), 'application/pdf'), () => {})
        }
      />

      {inv.docType !== 'ANGEBOT' && inv.status !== 'DRAFT' && (
        <Card>
          <CardHeader>
            <CardTitle>E-Rechnung</CardTitle>
            <CardDescription>
              ZUGFeRD: PDF mit eingebetteten Rechnungsdaten für Geschäftskunden. XRechnung: XML für Behörden
              (Leitweg-ID beim Kunden hinterlegen).
            </CardDescription>
          </CardHeader>
          <CardContent className="flex-row gap-2">
            <ActionButton
              title="ZUGFeRD-PDF"
              variant="outline"
              className="flex-1"
              disabled={busy}
              onPress={() =>
                run(
                  () => openDocument(token, `/api/invoices/${inv.id}/zugferd`, fileName(inv, 'zugferd'), 'application/pdf'),
                  () => {},
                )
              }
            />
            <ActionButton
              title="XRechnung (XML)"
              variant="outline"
              className="flex-1"
              disabled={busy}
              onPress={() =>
                run(
                  () => openDocument(token, `/api/invoices/${inv.id}/xrechnung`, fileName(inv, 'xrechnung'), 'application/xml'),
                  () => {},
                )
              }
            />
          </CardContent>
        </Card>
      )}

      {inv.status === 'DRAFT' && (
        <>
          <ActionButton title="Ausstellen (Nummer vergeben)" busy={busy} onPress={() => run(() => api.issue(token, inv.id))} />
          <Text variant="muted">
            Nach dem Ausstellen ist der Beleg festgeschrieben und kann nur noch storniert werden (GoBD).
          </Text>
          <ActionButton
            title="Entwurf löschen"
            variant="destructive"
            disabled={busy}
            onPress={() => run(() => api.deleteDraft(token, inv.id), () => router.back())}
          />
        </>
      )}

      {inv.docType === 'RECHNUNG' && inv.status === 'ISSUED' && (
        <ActionButton title="Als bezahlt markieren" busy={busy} onPress={() => run(() => api.markPaid(token, inv.id))} />
      )}

      {inv.docType === 'RECHNUNG' &&
        (inv.status === 'ISSUED' || inv.status === 'PAID') &&
        (confirmCancel ? (
          <Card className="border-destructive">
            <CardContent className="gap-3">
              <Text>Es wird eine Stornorechnung mit negativen Beträgen erstellt. Fortfahren?</Text>
              <ActionButton
                title="Ja, stornieren"
                variant="destructive"
                busy={busy}
                onPress={() => run(() => api.cancel(token, inv.id), (s) => s && router.replace(`/invoices/${s.id}`))}
              />
              <ActionButton title="Abbrechen" variant="outline" onPress={() => setConfirmCancel(false)} />
            </CardContent>
          </Card>
        ) : (
          <ActionButton title="Stornieren" variant="outline" onPress={() => setConfirmCancel(true)} />
        ))}
    </Screen>
  );
}
