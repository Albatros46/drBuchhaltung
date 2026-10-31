import { useFocusEffect } from 'expo-router';
import { useCallback, useState } from 'react';
import { StyleSheet, Text } from 'react-native';

import { Button, Card, colors, ErrorBox, Field, H2, Muted, Row, Screen } from '@/components/ui';
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
        <H2>Neuer Kunde</H2>
        <Field label="Name / Firma" value={form.name} onChangeText={set('name')} />
        <Field label="Straße und Hausnummer" value={form.street} onChangeText={set('street')} />
        <Row>
          <Field label="PLZ" value={form.zip} onChangeText={set('zip')} keyboardType="number-pad" />
          <Field label="Ort" value={form.city} onChangeText={set('city')} />
        </Row>
        <Field label="E-Mail" value={form.email} onChangeText={set('email')} autoCapitalize="none" />
        <Field label="USt-IdNr. (für B2B / Reverse-Charge)" value={form.ustId} onChangeText={set('ustId')} autoCapitalize="characters" />
        <ErrorBox message={error?.message ?? null} errors={error?.errors} />
        <Button title="Kunde speichern" onPress={save} busy={busy} />
      </Card>

      {customers.map((c) => (
        <Card key={c.id}>
          <Row>
            <Text style={styles.name}>{c.name}</Text>
            <Muted>Nr. {c.customerNumber}</Muted>
          </Row>
          <Muted>{[c.street, [c.zip, c.city].filter(Boolean).join(' ')].filter(Boolean).join(', ') || 'Keine Anschrift'}</Muted>
        </Card>
      ))}
    </Screen>
  );
}

const styles = StyleSheet.create({
  name: { fontWeight: '700', color: colors.ink },
});
