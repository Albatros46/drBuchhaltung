import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, Text } from 'react-native';

import { Button, Card, colors, ErrorBox, H2, Muted, Row, Screen, StatusPill } from '@/components/ui';
import { api, ApiError, Invoice } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { date, money } from '@/lib/format';

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
        <Row>
          <H2>{title}</H2>
          <StatusPill status={inv.status} />
        </Row>
        <Muted>
          Ausgestellt: {date(inv.issueDate)} · Leistung: {date(inv.serviceDateFrom)} · Fällig: {date(inv.dueDate)}
        </Muted>
      </Card>

      <Card>
        {inv.lines.map((l) => (
          <Row key={l.position} style={styles.line}>
            <Text style={styles.desc}>
              {l.description}
              {'\n'}
              <Text style={styles.sub}>
                {l.quantity} {l.unit} × {money(l.unitPrice)} · {l.vatRate} %
              </Text>
            </Text>
            <Text style={styles.num}>{money(l.netAmount)}</Text>
          </Row>
        ))}
        <Row><Text>Netto</Text><Text style={styles.num}>{money(inv.netTotal)}</Text></Row>
        <Row><Text>USt</Text><Text style={styles.num}>{money(inv.vatTotal)}</Text></Row>
        <Row><Text style={styles.total}>Gesamt</Text><Text style={[styles.num, styles.total]}>{money(inv.grossTotal)}</Text></Row>
        {inv.legalNote && <Muted>{inv.legalNote}</Muted>}
      </Card>

      <ErrorBox message={error?.message ?? null} errors={error?.errors} />

      {inv.status === 'DRAFT' && (
        <>
          <Button title="Ausstellen (Nummer vergeben)" busy={busy} onPress={() => run(() => api.issue(token, inv.id))} />
          <Muted>Nach dem Ausstellen ist der Beleg festgeschrieben und kann nur noch storniert werden (GoBD).</Muted>
          <Button
            title="Entwurf löschen"
            variant="danger"
            disabled={busy}
            onPress={() => run(() => api.deleteDraft(token, inv.id), () => router.back())}
          />
        </>
      )}

      {inv.docType === 'RECHNUNG' && inv.status === 'ISSUED' && (
        <Button title="Als bezahlt markieren" busy={busy} onPress={() => run(() => api.markPaid(token, inv.id))} />
      )}

      {inv.docType === 'RECHNUNG' && (inv.status === 'ISSUED' || inv.status === 'PAID') &&
        (confirmCancel ? (
          <Card style={{ borderColor: colors.danger }}>
            <Text>Es wird eine Stornorechnung mit negativen Beträgen erstellt. Fortfahren?</Text>
            <Button
              title="Ja, stornieren"
              variant="danger"
              busy={busy}
              onPress={() => run(() => api.cancel(token, inv.id), (s) => s && router.replace(`/invoices/${s.id}`))}
            />
            <Button title="Abbrechen" variant="ghost" onPress={() => setConfirmCancel(false)} />
          </Card>
        ) : (
          <Button title="Stornieren" variant="danger" onPress={() => setConfirmCancel(true)} />
        ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  line: { borderBottomWidth: 1, borderBottomColor: colors.line, paddingBottom: 6, alignItems: 'flex-start' },
  desc: { flex: 1, color: colors.ink, fontWeight: '600' },
  sub: { color: colors.muted, fontWeight: '400' },
  num: { fontVariant: ['tabular-nums'], color: colors.ink },
  total: { fontWeight: '700', fontSize: 16 },
});
