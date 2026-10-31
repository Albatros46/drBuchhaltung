import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { View } from 'react-native';

import { ActionButton, ErrorBox, Field, Screen } from '@/components/app-ui';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Text } from '@/components/ui/text';
import { api, ApiError, Customer } from '@/lib/api';
import { useToken } from '@/lib/auth';

const empty = { name: '', street: '', zip: '', city: '', email: '', ustId: '' };

export default function CustomersScreen() {
  const token = useToken();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [form, setForm] = useState(empty);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(() => {
    api.customers(token).then(setCustomers).catch((e: ApiError) => setError(e));
  }, [token]);
  useFocusEffect(load);

  const set = (k: keyof typeof empty) => (v: string) => setForm((f) => ({ ...f, [k]: v }));

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.createCustomer(token, {
        name: form.name,
        street: form.street || null,
        zip: form.zip || null,
        city: form.city || null,
        email: form.email || null,
        ustId: form.ustId || null,
      });
      setForm(empty);
      load();
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
          <CardTitle>Neuer Kunde</CardTitle>
        </CardHeader>
        <CardContent className="gap-3">
          <Field label="Name / Firma" value={form.name} onChangeText={set('name')} />
          <Field label="Straße und Hausnummer" value={form.street} onChangeText={set('street')} />
          <View className="flex-row gap-2">
            <Field label="PLZ" value={form.zip} onChangeText={set('zip')} keyboardType="number-pad" />
            <Field label="Ort" value={form.city} onChangeText={set('city')} className="flex-[2]" />
          </View>
          <Field label="E-Mail" value={form.email} onChangeText={set('email')} autoCapitalize="none" />
          <Field
            label="USt-IdNr. (für B2B / Reverse-Charge)"
            value={form.ustId}
            onChangeText={set('ustId')}
            autoCapitalize="characters"
          />
          <ErrorBox message={error?.message ?? null} errors={error?.errors} />
          <ActionButton title="Kunde speichern" onPress={save} busy={busy} />
        </CardContent>
      </Card>

      {customers.map((c) => (
        <Card key={c.id} className="py-4">
          <CardContent className="gap-1">
            <View className="flex-row items-center justify-between">
              <Text className="font-semibold">{c.name}</Text>
              <Text variant="muted">Nr. {c.customerNumber}</Text>
            </View>
            <Text variant="muted">
              {[c.street, [c.zip, c.city].filter(Boolean).join(' ')].filter(Boolean).join(', ') || 'Keine Anschrift'}
            </Text>
          </CardContent>
        </Card>
      ))}
    </Screen>
  );
}
