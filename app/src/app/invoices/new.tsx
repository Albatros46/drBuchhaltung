import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { View } from 'react-native';

import { ActionButton, AmountRow, Chip, ErrorBox, Field, Screen } from '@/components/app-ui';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Text } from '@/components/ui/text';
import { api, ApiError, Customer, DocType, LineInput, Product } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { money, parseDecimal, previewTotals, priceInput, today } from '@/lib/format';

interface LineDraft {
  description: string;
  quantity: string;
  unit: string;
  unitPrice: string;
  vatRate: number;
}

const emptyLine = (): LineDraft => ({ description: '', quantity: '1', unit: 'Stk', unitPrice: '', vatRate: 19 });
const RATES = [19, 7, 0];

export default function NewInvoiceScreen() {
  const token = useToken();
  const router = useRouter();
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [customerId, setCustomerId] = useState<string | null>(null);
  const [docType, setDocType] = useState<DocType>('RECHNUNG');
  const [serviceDate, setServiceDate] = useState(today());
  const [lines, setLines] = useState<LineDraft[]>([emptyLine()]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  useFocusEffect(
    useCallback(() => {
      api.customers(token).then(setCustomers).catch((e: ApiError) => setError(e));
      // ohne Artikel bleibt die Auswahl einfach weg
      api.products(token).then(setProducts).catch(() => setProducts([]));
    }, [token]),
  );

  const parsed: LineInput[] = lines.map((l) => ({
    description: l.description.trim(),
    quantity: parseDecimal(l.quantity),
    unit: l.unit.trim(),
    unitPrice: parseDecimal(l.unitPrice),
    vatRate: l.vatRate,
  }));
  const totals = previewTotals(parsed);

  const update = (i: number, patch: Partial<LineDraft>) =>
    setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)));

  // Artikel fuellt eine leere letzte Position, sonst kommt eine neue dazu
  const addProduct = (p: Product) => {
    const line: LineDraft = {
      description: p.description ? `${p.name} – ${p.description}` : p.name,
      quantity: '1',
      unit: p.unit,
      unitPrice: priceInput(p.unitPrice),
      vatRate: p.vatRate,
    };
    setLines((prev) => {
      const last = prev[prev.length - 1];
      const lastEmpty = last && !last.description.trim() && !last.unitPrice.trim();
      return lastEmpty ? [...prev.slice(0, -1), line] : [...prev, line];
    });
  };

  const save = async (issueNow: boolean) => {
    setError(null);
    if (!customerId) return setError(new ApiError('Bitte einen Kunden auswählen', 400));
    const invalid = parsed.findIndex(
      (l) => !l.description || !l.unit || !(l.quantity > 0) || !Number.isFinite(l.unitPrice),
    );
    if (invalid >= 0) {
      return setError(new ApiError(`Position ${invalid + 1}: Beschreibung, Menge, Einheit und Preis angeben`, 400));
    }
    setBusy(true);
    try {
      const draft = await api.createInvoice(token, {
        customerId,
        docType,
        serviceDateFrom: serviceDate || null,
        lines: parsed,
      });
      // Ausstellen uebernimmt die Detailseite: so bleibt der gespeicherte Entwurf sichtbar,
      // falls Pflichtangaben fehlen, und es entsteht beim erneuten Tippen kein zweiter Entwurf.
      router.replace({ pathname: '/invoices/[id]', params: { id: draft.id, ...(issueNow ? { issue: '1' } : {}) } });
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
          <CardTitle>Belegart</CardTitle>
        </CardHeader>
        <CardContent className="flex-row gap-2">
          {(['RECHNUNG', 'ANGEBOT'] as DocType[]).map((t) => (
            <Chip
              key={t}
              label={t === 'RECHNUNG' ? 'Rechnung' : 'Angebot'}
              active={docType === t}
              onPress={() => setDocType(t)}
            />
          ))}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Kunde</CardTitle>
        </CardHeader>
        <CardContent className="gap-3">
          {customers.length === 0 && (
            <Text variant="muted">Noch keine Kunden. Legen Sie unter „Kunden“ zuerst einen an.</Text>
          )}
          <View className="flex-row flex-wrap gap-2">
            {customers.map((c) => (
              <Chip key={c.id} label={c.name} active={customerId === c.id} onPress={() => setCustomerId(c.id)} />
            ))}
          </View>
          <Field label="Leistungsdatum (JJJJ-MM-TT)" value={serviceDate} onChangeText={setServiceDate} />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Positionen</CardTitle>
        </CardHeader>
        <CardContent className="gap-4">
          {products.length > 0 && (
            <View className="gap-2">
              <Text variant="muted">Aus Artikeln übernehmen:</Text>
              <View className="flex-row flex-wrap gap-2">
                {products.map((p) => (
                  <Chip key={p.id} label={`+ ${p.name}`} active={false} onPress={() => addProduct(p)} />
                ))}
              </View>
              <Separator />
            </View>
          )}
          {lines.map((l, i) => (
            <View key={i} className="gap-3">
              {i > 0 && <Separator />}
              <Field
                label={`Pos. ${i + 1} Beschreibung`}
                value={l.description}
                onChangeText={(v) => update(i, { description: v })}
                placeholder="Fliesen verlegen"
              />
              <View className="flex-row gap-2">
                <Field label="Menge" value={l.quantity} onChangeText={(v) => update(i, { quantity: v })} keyboardType="decimal-pad" />
                <Field label="Einheit" value={l.unit} onChangeText={(v) => update(i, { unit: v })} />
                <Field
                  label="Einzelpreis €"
                  value={l.unitPrice}
                  onChangeText={(v) => update(i, { unitPrice: v })}
                  keyboardType="decimal-pad"
                  placeholder="85,00"
                />
              </View>
              <View className="flex-row items-center justify-between">
                <View className="flex-row gap-2">
                  {RATES.map((r) => (
                    <Chip key={r} label={`${r} %`} active={l.vatRate === r} onPress={() => update(i, { vatRate: r })} />
                  ))}
                </View>
                {lines.length > 1 && (
                  <Button variant="ghost" size="sm" onPress={() => setLines((prev) => prev.filter((_, idx) => idx !== i))}>
                    <Text className="text-destructive">Entfernen</Text>
                  </Button>
                )}
              </View>
            </View>
          ))}
          <ActionButton title="+ Position" variant="outline" onPress={() => setLines((prev) => [...prev, emptyLine()])} />
        </CardContent>
      </Card>

      <Card>
        <CardContent className="gap-2">
          <AmountRow label="Netto" value={money(totals.net)} />
          <AmountRow label="USt" value={money(totals.vat)} />
          <Separator />
          <AmountRow label="Gesamt" value={money(totals.gross)} strong />
          <Text variant="muted">Bei Kleinunternehmern (§ 19) und Reverse-Charge berechnet das Backend 0 % USt.</Text>
        </CardContent>
      </Card>

      <ErrorBox message={error?.message ?? null} errors={error?.errors} />
      <ActionButton
        title={docType === 'RECHNUNG' ? 'Rechnung ausstellen' : 'Angebot ausstellen'}
        onPress={() => save(true)}
        busy={busy}
      />
      <ActionButton title="Als Entwurf speichern" variant="outline" onPress={() => save(false)} disabled={busy} />
    </Screen>
  );
}
