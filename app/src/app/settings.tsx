import { useEffect, useState } from 'react';
import { View } from 'react-native';

import { ActionButton, Chip, ErrorBox, Field, Screen } from '@/components/app-ui';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Text } from '@/components/ui/text';
import { api, API_URL, ApiError, Organization } from '@/lib/api';
import { useToken } from '@/lib/auth';

const text = (v: string | null | undefined) => v ?? '';
const orNull = (v: string) => (v.trim() ? v.trim() : null);
const intOrNull = (v: string) => (/^\d+$/.test(v.trim()) ? Number(v.trim()) : null);

export default function SettingsScreen() {
  const token = useToken();
  const [org, setOrg] = useState<Organization | null>(null);
  const [berater, setBerater] = useState('');
  const [mandant, setMandant] = useState('');
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const year = new Date().getFullYear();

  useEffect(() => {
    api
      .organization(token)
      .then((o) => {
        setOrg(o);
        setBerater(o.datevBeraterNr?.toString() ?? '');
        setMandant(o.datevMandantNr?.toString() ?? '');
      })
      .catch((e: ApiError) => setError(e));
  }, [token]);

  if (!org) {
    return (
      <Screen>
        <ErrorBox message={error?.message ?? null} />
      </Screen>
    );
  }

  const set = (k: keyof Organization) => (v: string) => {
    setSaved(false);
    setOrg({ ...org, [k]: orNull(v) });
  };

  const save = async () => {
    setBusy(true);
    setError(null);
    try {
      const res = await api.saveOrganization(token, {
        ...org,
        datevBeraterNr: intOrNull(berater),
        datevMandantNr: intOrNull(mandant),
      });
      setOrg(res);
      setSaved(true);
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
          <CardTitle>Firma</CardTitle>
          <CardDescription>Pflichtangaben nach § 14 UStG, die auf jeder Rechnung stehen.</CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          <Field label="Firmenname" value={org.name} onChangeText={(v) => setOrg({ ...org, name: v })} />
          <Field label="Straße und Hausnummer" value={text(org.street)} onChangeText={set('street')} />
          <View className="flex-row gap-2">
            <Field label="PLZ" value={text(org.zip)} onChangeText={set('zip')} keyboardType="number-pad" />
            <Field label="Ort" value={text(org.city)} onChangeText={set('city')} className="flex-[2]" />
          </View>
          <Field label="Steuernummer" value={text(org.steuernummer)} onChangeText={set('steuernummer')} placeholder="12/345/67890" />
          <Field label="USt-IdNr." value={text(org.ustId)} onChangeText={set('ustId')} autoCapitalize="characters" />
          <View className="flex-row items-center justify-between">
            <Label nativeID="kleinunternehmer">Kleinunternehmer (§ 19 UStG)</Label>
            <Switch
              aria-labelledby="kleinunternehmer"
              checked={org.kleinunternehmer}
              onCheckedChange={(v) => setOrg({ ...org, kleinunternehmer: v })}
            />
          </View>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Bank</CardTitle>
        </CardHeader>
        <CardContent className="gap-3">
          <Field label="IBAN" value={text(org.iban)} onChangeText={set('iban')} autoCapitalize="characters" />
          <Field label="BIC" value={text(org.bic)} onChangeText={set('bic')} autoCapitalize="characters" />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>DATEV</CardTitle>
          <CardDescription>Kontenrahmen und Nummern von Ihrem Steuerberater.</CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          <View className="flex-row gap-2">
            {(['SKR03', 'SKR04'] as const).map((k) => (
              <Chip key={k} label={k} active={org.kontenrahmen === k} onPress={() => setOrg({ ...org, kontenrahmen: k })} />
            ))}
          </View>
          <View className="flex-row gap-2">
            <Field label="Beraternummer" value={berater} onChangeText={setBerater} keyboardType="number-pad" />
            <Field label="Mandantennummer" value={mandant} onChangeText={setMandant} keyboardType="number-pad" />
          </View>
          <Text variant="muted" selectable>
            Export für {year}: GET {API_URL}/api/exports/datev?from={year}-01-01&to={year}-12-31
          </Text>
        </CardContent>
      </Card>

      <ErrorBox message={error?.message ?? null} errors={error?.errors} />
      {saved && <Text className="font-semibold text-primary">Gespeichert</Text>}
      <ActionButton title="Speichern" onPress={save} busy={busy} />
    </Screen>
  );
}
