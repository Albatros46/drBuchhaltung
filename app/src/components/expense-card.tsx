import { useEffect, useState } from 'react';
import { View } from 'react-native';

import { ActionButton, Chip, ErrorBox, Field } from '@/components/app-ui';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Text } from '@/components/ui/text';
import { api, ApiError, ExpenseCategoryOption, IncomingInvoice } from '@/lib/api';

/** Gegenkonto der Eingangsrechnung fuer den DATEV-Export waehlen. */
export function ExpenseCard({
  token,
  inv,
  onSaved,
}: {
  token: string;
  inv: IncomingInvoice;
  onSaved: (inv: IncomingInvoice) => void;
}) {
  const [options, setOptions] = useState<ExpenseCategoryOption[]>([]);
  const [editing, setEditing] = useState(false);
  const [category, setCategory] = useState(inv.expense.category);
  const [account, setAccount] = useState('');
  const [buKey, setBuKey] = useState('');
  const [remember, setRemember] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    if (editing && options.length === 0) {
      api.expenseCategories(token).then(setOptions).catch((e: ApiError) => setError(e));
    }
  }, [editing, options.length, token]);

  const startEditing = () => {
    setCategory(inv.expense.category);
    setAccount(inv.expense.category === 'CUSTOM' ? inv.expense.accounts : '');
    setBuKey(inv.expense.category === 'CUSTOM' ? (inv.expense.buKey ?? '') : '');
    setError(null);
    setEditing(true);
  };

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const custom = category === 'CUSTOM';
      const saved = await api.assignExpense(token, inv.id, {
        category,
        account: custom && account ? Number(account) : undefined,
        buKey: custom ? buKey : undefined,
        rememberForSupplier: remember,
      });
      onSaved(saved);
      setEditing(false);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  const selected = options.find((o) => o.code === category);

  return (
    <Card>
      <CardHeader>
        <View className="flex-row items-center justify-between gap-2">
          <CardTitle className="flex-1">Kontierung</CardTitle>
          <Badge variant={inv.expense.chosen ? 'default' : 'outline'}>
            <Text>{inv.expense.chosen ? 'gewählt' : 'Vorschlag'}</Text>
          </Badge>
        </View>
        <CardDescription>Gegenkonto im DATEV-Export. Im Zweifel mit dem Steuerberater abstimmen.</CardDescription>
      </CardHeader>
      <CardContent className="gap-3">
        <View className="flex-row justify-between gap-3">
          <Text className="flex-1 font-semibold">{inv.expense.label}</Text>
          <Text className="tabular-nums">{inv.expense.accounts}</Text>
        </View>
        {inv.expense.buKey ? <Text variant="muted">BU-Schlüssel {inv.expense.buKey}</Text> : null}
        {inv.expense.reverseCharge && (
          <Text variant="muted">Reverse Charge erkannt: Der Leistungsempfänger schuldet die Umsatzsteuer (§ 13b UStG).</Text>
        )}

        {!editing ? (
          <Button variant="outline" onPress={startEditing}>
            <Text>Kontierung ändern</Text>
          </Button>
        ) : (
          <View className="gap-3">
            <View className="flex-row flex-wrap gap-2">
              {options.map((o) => (
                <Chip key={o.code} label={o.label} active={category === o.code} onPress={() => setCategory(o.code)} />
              ))}
            </View>
            {selected && (
              <Text variant="muted">
                {selected.accounts ? `Konto ${selected.accounts} · ` : ''}
                {selected.taxNote}
              </Text>
            )}
            {category === 'CUSTOM' && (
              <View className="flex-row gap-3">
                <Field
                  label="Konto"
                  value={account}
                  onChangeText={(v) => setAccount(v.replace(/\D/g, ''))}
                  keyboardType="number-pad"
                  placeholder="4980"
                  className="flex-[2]"
                />
                <Field
                  label="BU-Schlüssel"
                  value={buKey}
                  onChangeText={(v) => setBuKey(v.replace(/\D/g, ''))}
                  keyboardType="number-pad"
                  placeholder="9"
                  maxLength={4}
                  className="flex-1"
                />
              </View>
            )}
            <View className="flex-row items-center justify-between gap-3">
              <Label nativeID="remember-expense" className="flex-1">
                Für diesen Lieferanten merken
              </Label>
              <Switch aria-labelledby="remember-expense" checked={remember} onCheckedChange={setRemember} />
            </View>
            <ErrorBox message={error?.message ?? null} errors={error?.errors} />
            <View className="flex-row gap-2">
              <Button variant="ghost" className="flex-1" disabled={busy} onPress={() => setEditing(false)}>
                <Text>Abbrechen</Text>
              </Button>
              <View className="flex-1">
                <ActionButton title="Speichern" busy={busy} onPress={save} />
              </View>
            </View>
          </View>
        )}
      </CardContent>
    </Card>
  );
}
