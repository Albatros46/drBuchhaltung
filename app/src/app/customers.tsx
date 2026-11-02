import { useFocusEffect } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { View } from 'react-native';

import { ActionButton, ErrorBox, Field, Screen, type ScrollRef } from '@/components/app-ui';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { api, ApiError, Customer } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { openDocument } from '@/lib/download';

const empty = { name: '', street: '', zip: '', city: '', email: '', ustId: '', leitwegId: '' };
type Form = typeof empty;

const toForm = (c: Customer): Form => ({
  name: c.name,
  street: c.street ?? '',
  zip: c.zip ?? '',
  city: c.city ?? '',
  email: c.email ?? '',
  ustId: c.ustId ?? '',
  leitwegId: c.leitwegId ?? '',
});

export default function CustomersScreen() {
  const token = useToken();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [form, setForm] = useState(empty);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const scrollRef: ScrollRef = useRef(null);

  const load = useCallback(() => {
    api.customers(token).then(setCustomers).catch((e: ApiError) => setError(e));
  }, [token]);
  useFocusEffect(load);

  const set = (k: keyof Form) => (v: string) => setForm((f) => ({ ...f, [k]: v }));

  const reset = () => {
    setForm(empty);
    setEditingId(null);
    setError(null);
  };

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      return true;
    } catch (e) {
      setError(e as ApiError);
      return false;
    } finally {
      setBusy(false);
    }
  };

  const save = async () => {
    const body = {
      name: form.name,
      street: form.street || null,
      zip: form.zip || null,
      city: form.city || null,
      email: form.email || null,
      ustId: form.ustId || null,
      leitwegId: form.leitwegId || null,
    };
    const ok = await run(() => (editingId ? api.updateCustomer(token, editingId, body) : api.createCustomer(token, body)));
    if (ok) {
      reset();
      load();
    }
  };

  const remove = async (id: string) => {
    setConfirmId(null);
    if (await run(() => api.deleteCustomer(token, id))) {
      if (editingId === id) reset();
      load();
    }
  };

  const q = query.trim().toLowerCase();
  const visible = q
    ? customers.filter((c) =>
        [c.name, c.city, c.email, c.ustId, String(c.customerNumber)].some((v) => v?.toLowerCase().includes(q)),
      )
    : customers;

  return (
    <Screen scrollRef={scrollRef}>
      <Card>
        <CardHeader>
          <CardTitle>{editingId ? 'Kunde bearbeiten' : 'Neuer Kunde'}</CardTitle>
        </CardHeader>
        <CardContent className="gap-3">
          <Field label="Name / Firma" value={form.name} onChangeText={set('name')} />
          <Field label="Straße und Hausnummer" value={form.street} onChangeText={set('street')} />
          <View className="flex-row gap-2">
            <Field label="PLZ" value={form.zip} onChangeText={set('zip')} keyboardType="number-pad" />
            <Field label="Ort" value={form.city} onChangeText={set('city')} className="flex-[2]" />
          </View>
          <Field
            label="E-Mail (Pflicht für XRechnung)"
            value={form.email}
            onChangeText={set('email')}
            autoCapitalize="none"
            keyboardType="email-address"
          />
          <Field
            label="USt-IdNr. (für B2B / Reverse-Charge)"
            value={form.ustId}
            onChangeText={set('ustId')}
            autoCapitalize="characters"
          />
          <Field
            label="Leitweg-ID (nur Behörden)"
            value={form.leitwegId}
            onChangeText={set('leitwegId')}
            placeholder="z. B. 05315000-12345-67"
          />
          <ErrorBox message={error?.message ?? null} errors={error?.errors} />
          <View className="flex-row gap-2">
            {editingId && (
              <Button variant="ghost" className="flex-1" disabled={busy} onPress={reset}>
                <Text>Abbrechen</Text>
              </Button>
            )}
            <View className="flex-1">
              <ActionButton title={editingId ? 'Änderungen speichern' : 'Kunde speichern'} onPress={save} busy={busy} />
            </View>
          </View>
        </CardContent>
      </Card>

      <View className="mt-2 flex-row items-center gap-2">
        <Text className="flex-1 font-semibold">Kunden ({customers.length})</Text>
        <Button
          variant="outline"
          size="sm"
          onPress={() => run(() => openDocument(token, '/api/customers/export', 'Kunden.csv', 'text/csv'))}>
          <Text>CSV exportieren</Text>
        </Button>
      </View>
      <Input
        value={query}
        onChangeText={setQuery}
        placeholder="Suchen: Name, Ort, E-Mail, USt-IdNr., Nummer"
        accessibilityLabel="Kunden suchen"
      />

      {visible.map((c) => (
        <Card key={c.id} className={editingId === c.id ? 'border-primary py-4' : 'py-4'}>
          <CardContent className="gap-1">
            <View className="flex-row items-center justify-between">
              <Text className="flex-1 font-semibold">{c.name}</Text>
              <Text variant="muted">Nr. {c.customerNumber}</Text>
            </View>
            <Text variant="muted">
              {[c.street, [c.zip, c.city].filter(Boolean).join(' ')].filter(Boolean).join(', ') || 'Keine Anschrift'}
            </Text>
            {c.email ? <Text variant="muted">{c.email}</Text> : null}
            {confirmId === c.id ? (
              <View className="mt-2 flex-row items-center gap-2">
                <Text className="flex-1 text-destructive">Kunde wirklich löschen?</Text>
                <Button variant="ghost" size="sm" onPress={() => setConfirmId(null)}>
                  <Text>Nein</Text>
                </Button>
                <Button variant="destructive" size="sm" disabled={busy} onPress={() => remove(c.id)}>
                  <Text>Löschen</Text>
                </Button>
              </View>
            ) : (
              <View className="mt-2 flex-row justify-end gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onPress={() => {
                    setEditingId(c.id);
                    setForm(toForm(c));
                    setError(null);
                    scrollRef.current?.scrollTo({ y: 0, animated: true });
                  }}>
                  <Text>Bearbeiten</Text>
                </Button>
                <Button variant="ghost" size="sm" onPress={() => setConfirmId(c.id)}>
                  <Text className="text-destructive">Löschen</Text>
                </Button>
              </View>
            )}
          </CardContent>
        </Card>
      ))}
      {visible.length === 0 && customers.length > 0 && <Text variant="muted">Kein Kunde passt zur Suche.</Text>}
    </Screen>
  );
}
