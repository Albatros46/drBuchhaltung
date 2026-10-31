import { Link, Redirect, useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';

import { Button, Card, colors, ErrorBox, Muted, Row, Screen, StatusPill } from '@/components/ui';
import { api, ApiError, Customer, Invoice } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { date, money } from '@/lib/format';

export default function OverviewScreen() {
  const { session, loading, signOut } = useAuth();
  const router = useRouter();
  const [invoices, setInvoices] = useState<Invoice[] | null>(null);
  const [customers, setCustomers] = useState<Record<string, Customer>>({});
  const [error, setError] = useState<string | null>(null);

  useFocusEffect(
    useCallback(() => {
      if (!session) return;
      Promise.all([api.invoices(session.token), api.customers(session.token)])
        .then(([inv, cus]) => {
          setInvoices(inv);
          setCustomers(Object.fromEntries(cus.map((c) => [c.id, c])));
          setError(null);
        })
        .catch((e: ApiError) => {
          if (e.status === 401) signOut();
          else setError(e.message);
        });
    }, [session, signOut]),
  );

  if (loading) return <ActivityIndicator style={{ marginTop: 40 }} />;
  if (!session) return <Redirect href="/login" />;

  const realInvoices = (invoices ?? []).filter((i) => i.docType !== 'ANGEBOT' && i.status !== 'DRAFT');
  const open = realInvoices.filter((i) => i.status === 'ISSUED');
  const overdue = open.filter((i) => i.dueDate && i.dueDate < new Date().toISOString().slice(0, 10));
  const revenue = realInvoices.reduce((sum, i) => sum + i.netTotal, 0);

  return (
    <Screen>
      <Row>
        <View>
          <Text style={styles.company}>{session.companyName}</Text>
          <Muted>{session.email}</Muted>
        </View>
        <Pressable onPress={signOut}>
          <Text style={styles.link}>Abmelden</Text>
        </Pressable>
      </Row>

      <View style={styles.kpis}>
        <Card style={styles.kpi}>
          <Muted>Umsatz netto</Muted>
          <Text style={styles.kpiValue}>{money(revenue)}</Text>
        </Card>
        <Card style={styles.kpi}>
          <Muted>Offen</Muted>
          <Text style={styles.kpiValue}>{money(open.reduce((s, i) => s + i.grossTotal, 0))}</Text>
        </Card>
      </View>

      {overdue.length > 0 && (
        <Card style={{ borderColor: colors.danger }}>
          <Text style={{ color: colors.danger, fontWeight: '700' }}>
            {overdue.length} Rechnung(en) überfällig
          </Text>
        </Card>
      )}

      <Button title="+ Neue Rechnung" onPress={() => router.push('/invoices/new')} />
      <Row>
        <Link href="/customers" style={styles.link}>
          Kunden
        </Link>
        <Link href="/settings" style={styles.link}>
          Firma und Steuer
        </Link>
      </Row>

      <ErrorBox message={error} />
      {invoices === null && !error && <ActivityIndicator />}
      {invoices?.length === 0 && (
        <Card>
          <Muted>Noch keine Belege. Legen Sie zuerst einen Kunden an und erstellen Sie dann Ihre erste Rechnung.</Muted>
        </Card>
      )}
      {invoices?.map((inv) => (
        <Pressable key={inv.id} onPress={() => router.push(`/invoices/${inv.id}`)}>
          <Card>
            <Row>
              <Text style={styles.number}>{inv.number ?? `Entwurf (${inv.docType.toLowerCase()})`}</Text>
              <StatusPill status={inv.status} />
            </Row>
            <Row>
              <Text style={styles.ink}>{customers[inv.customerId]?.name ?? '–'}</Text>
              <Text style={styles.amount}>{money(inv.grossTotal)}</Text>
            </Row>
            {inv.issueDate && <Muted>{date(inv.issueDate)}</Muted>}
          </Card>
        </Pressable>
      ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  company: { fontSize: 20, fontWeight: '700', color: colors.ink },
  link: { color: colors.accent, fontWeight: '700', paddingVertical: 6 },
  kpis: { flexDirection: 'row', gap: 12 },
  kpi: { flex: 1 },
  kpiValue: { fontSize: 20, fontWeight: '700', color: colors.ink, fontVariant: ['tabular-nums'] },
  number: { fontWeight: '700', color: colors.ink },
  ink: { color: colors.ink },
  amount: { color: colors.ink, fontVariant: ['tabular-nums'] },
});
