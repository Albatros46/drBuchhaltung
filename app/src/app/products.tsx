import { useFocusEffect } from 'expo-router';
import { useCallback, useRef, useState } from 'react';
import { View } from 'react-native';

import { ActionButton, Chip, ErrorBox, Field, Screen, type ScrollRef } from '@/components/app-ui';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import { api, ApiError, Product } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { openDocument } from '@/lib/download';
import { money, parseDecimal, priceInput } from '@/lib/format';

const RATES = [19, 7, 0];
const empty = { name: '', description: '', unit: 'Stk', unitPrice: '', vatRate: 19 };
type Form = typeof empty;

const toForm = (p: Product): Form => ({
  name: p.name,
  description: p.description ?? '',
  unit: p.unit,
  unitPrice: priceInput(p.unitPrice),
  vatRate: p.vatRate,
});

export default function ProductsScreen() {
  const token = useToken();
  const [products, setProducts] = useState<Product[]>([]);
  const [form, setForm] = useState(empty);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [confirmId, setConfirmId] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const scrollRef: ScrollRef = useRef(null);

  const load = useCallback(() => {
    api.products(token).then(setProducts).catch((e: ApiError) => setError(e));
  }, [token]);
  useFocusEffect(load);

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
    const unitPrice = parseDecimal(form.unitPrice);
    if (!Number.isFinite(unitPrice)) {
      setError(new ApiError('Einzelpreis: bitte einen Betrag wie 48,50 eingeben', 400));
      return;
    }
    const body = {
      name: form.name,
      description: form.description.trim() || null,
      unit: form.unit,
      unitPrice,
      vatRate: form.vatRate,
    };
    const ok = await run(() => (editingId ? api.updateProduct(token, editingId, body) : api.createProduct(token, body)));
    if (ok) {
      reset();
      load();
    }
  };

  const remove = async (id: string) => {
    setConfirmId(null);
    if (await run(() => api.deleteProduct(token, id))) {
      if (editingId === id) reset();
      load();
    }
  };

  const q = query.trim().toLowerCase();
  const visible = q
    ? products.filter((p) => [p.name, p.description, String(p.productNumber)].some((v) => v?.toLowerCase().includes(q)))
    : products;

  return (
    <Screen scrollRef={scrollRef}>
      <Card>
        <CardHeader>
          <CardTitle>{editingId ? 'Artikel bearbeiten' : 'Neuer Artikel'}</CardTitle>
        </CardHeader>
        <CardContent className="gap-3">
          <Field
            label="Bezeichnung"
            value={form.name}
            onChangeText={(v) => setForm((f) => ({ ...f, name: v }))}
            placeholder="Fliesen verlegen"
          />
          <Field
            label="Beschreibung (optional)"
            value={form.description}
            onChangeText={(v) => setForm((f) => ({ ...f, description: v }))}
          />
          <View className="flex-row gap-2">
            <Field label="Einheit" value={form.unit} onChangeText={(v) => setForm((f) => ({ ...f, unit: v }))} />
            <Field
              label="Einzelpreis netto €"
              value={form.unitPrice}
              onChangeText={(v) => setForm((f) => ({ ...f, unitPrice: v }))}
              keyboardType="decimal-pad"
              placeholder="48,50"
              className="flex-[2]"
            />
          </View>
          <View className="flex-row gap-2">
            {RATES.map((r) => (
              <Chip key={r} label={`${r} %`} active={form.vatRate === r} onPress={() => setForm((f) => ({ ...f, vatRate: r }))} />
            ))}
          </View>
          <ErrorBox message={error?.message ?? null} errors={error?.errors} />
          <View className="flex-row gap-2">
            {editingId && (
              <Button variant="ghost" className="flex-1" disabled={busy} onPress={reset}>
                <Text>Abbrechen</Text>
              </Button>
            )}
            <View className="flex-1">
              <ActionButton title={editingId ? 'Änderungen speichern' : 'Artikel speichern'} onPress={save} busy={busy} />
            </View>
          </View>
        </CardContent>
      </Card>

      <View className="mt-2 flex-row items-center gap-2">
        <Text className="flex-1 font-semibold">Artikel ({products.length})</Text>
        <Button
          variant="outline"
          size="sm"
          onPress={() => run(() => openDocument(token, '/api/products/export', 'Artikel.csv', 'text/csv'))}>
          <Text>CSV exportieren</Text>
        </Button>
      </View>
      <Input value={query} onChangeText={setQuery} placeholder="Suchen: Bezeichnung, Beschreibung, Nummer" accessibilityLabel="Artikel suchen" />

      {products.length === 0 && (
        <Text variant="muted">
          Noch keine Artikel. Legen Sie häufige Leistungen an, um sie in Rechnungen mit einem Tipp zu übernehmen.
        </Text>
      )}
      {visible.map((p) => (
        <Card key={p.id} className={editingId === p.id ? 'border-primary py-4' : 'py-4'}>
          <CardContent className="gap-1">
            <View className="flex-row items-center justify-between gap-3">
              <Text className="flex-1 font-semibold">{p.name}</Text>
              <Text className="tabular-nums">
                {money(p.unitPrice)} / {p.unit}
              </Text>
            </View>
            <Text variant="muted">
              Nr. {p.productNumber} · {p.vatRate} % USt{p.description ? ` · ${p.description}` : ''}
            </Text>
            {confirmId === p.id ? (
              <View className="mt-2 flex-row items-center gap-2">
                <Text className="flex-1 text-destructive">Artikel wirklich löschen?</Text>
                <Button variant="ghost" size="sm" onPress={() => setConfirmId(null)}>
                  <Text>Nein</Text>
                </Button>
                <Button variant="destructive" size="sm" disabled={busy} onPress={() => remove(p.id)}>
                  <Text>Löschen</Text>
                </Button>
              </View>
            ) : (
              <View className="mt-2 flex-row justify-end gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onPress={() => {
                    setEditingId(p.id);
                    setForm(toForm(p));
                    setError(null);
                    scrollRef.current?.scrollTo({ y: 0, animated: true });
                  }}>
                  <Text>Bearbeiten</Text>
                </Button>
                <Button variant="ghost" size="sm" onPress={() => setConfirmId(p.id)}>
                  <Text className="text-destructive">Löschen</Text>
                </Button>
              </View>
            )}
          </CardContent>
        </Card>
      ))}
      {visible.length === 0 && products.length > 0 && <Text variant="muted">Kein Artikel passt zur Suche.</Text>}
    </Screen>
  );
}
