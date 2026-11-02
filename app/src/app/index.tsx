import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { ErrorBox, Screen, StatusPill } from '@/components/app-ui';
import { Card, CardContent } from '@/components/ui/card';
import { Text } from '@/components/ui/text';
import { api, ApiError, Customer, Invoice } from '@/lib/api';
import { useAuth } from '@/lib/auth';
import { date, money } from '@/lib/format';

export default function OverviewScreen() {
  const { session, signOut } = useAuth();
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

  // Stack.Protected im Root-Layout zeigt diesen Bildschirm nur mit Sitzung.
  if (!session) return null;

  const realInvoices = (invoices ?? []).filter((i) => i.docType !== 'ANGEBOT' && i.status !== 'DRAFT');
  const open = realInvoices.filter((i) => i.status === 'ISSUED');
  const overdue = open.filter((i) => i.dueDate && i.dueDate < new Date().toISOString().slice(0, 10));
  const revenue = realInvoices.reduce((sum, i) => sum + i.netTotal, 0);

  return (
    <Screen>
      <View className="flex-row gap-3">
        <Card className="flex-1 gap-1 py-4">
          <CardContent className="gap-1">
            <Text variant="muted">Umsatz netto</Text>
            <Text className="text-xl font-bold tabular-nums">{money(revenue)}</Text>
          </CardContent>
        </Card>
        <Card className="flex-1 gap-1 py-4">
          <CardContent className="gap-1">
            <Text variant="muted">Offen</Text>
            <Text className="text-xl font-bold tabular-nums">
              {money(open.reduce((s, i) => s + i.grossTotal, 0))}
            </Text>
          </CardContent>
        </Card>
      </View>

      {overdue.length > 0 && (
        <View className="rounded-md border border-destructive bg-destructive/10 p-3">
          <Text className="font-semibold text-destructive">{overdue.length} Rechnung(en) überfällig</Text>
        </View>
      )}

      <Text className="mt-2 font-semibold">Belege</Text>
      <ErrorBox message={error} />
      {invoices === null && !error && <ActivityIndicator />}
      {invoices?.length === 0 && (
        <Card>
          <CardContent>
            <Text variant="muted">
              Noch keine Belege. Legen Sie zuerst einen Kunden an und erstellen Sie dann Ihre erste Rechnung.
            </Text>
          </CardContent>
        </Card>
      )}
      {invoices?.map((inv) => (
        <Pressable key={inv.id} onPress={() => router.push(`/invoices/${inv.id}`)}>
          <Card className="py-4">
            <CardContent className="gap-1">
              <View className="flex-row items-center justify-between">
                <Text className="font-semibold">
                  {inv.number ?? `Entwurf (${inv.docType === 'ANGEBOT' ? 'Angebot' : 'Rechnung'})`}
                </Text>
                <StatusPill status={inv.status} docType={inv.docType} />
              </View>
              <View className="flex-row items-center justify-between">
                <Text>{customers[inv.customerId]?.name ?? '–'}</Text>
                <Text className="tabular-nums">{money(inv.grossTotal)}</Text>
              </View>
              {inv.issueDate && <Text variant="muted">{date(inv.issueDate)}</Text>}
            </CardContent>
          </Card>
        </Pressable>
      ))}
    </Screen>
  );
}
