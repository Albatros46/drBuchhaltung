import { useEffect, useState } from 'react';
import { View } from 'react-native';

import { ActionButton, Chip, ErrorBox, Field, Screen } from '@/components/app-ui';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Text } from '@/components/ui/text';
import { api, ApiError, Organization } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { openDocument } from '@/lib/download';
import { parseDecimal } from '@/lib/format';

const text = (v: string | null | undefined) => v ?? '';
const orNull = (v: string) => (v.trim() ? v.trim() : null);
const intOrNull = (v: string) => (/^\d+$/.test(v.trim()) ? Number(v.trim()) : null);

export default function SettingsScreen() {
  const token = useToken();
  const [org, setOrg] = useState<Organization | null>(null);
  const [berater, setBerater] = useState('');
  const [fee, setFee] = useState('');
  const [mandant, setMandant] = useState('');
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const year = new Date().getFullYear();
  const [exportYear, setExportYear] = useState(year);

  const downloadDatev = async () => {
    setError(null);
    try {
      await openDocument(
        token,
        `/api/exports/datev?from=${exportYear}-01-01&to=${exportYear}-12-31`,
        `EXTF_Buchungsstapel_${exportYear}.csv`,
        'text/csv',
      );
    } catch (e) {
      setError(e as ApiError);
    }
  };

  useEffect(() => {
    api
      .organization(token)
      .then((o) => {
        setOrg(o);
        setBerater(o.datevBeraterNr?.toString() ?? '');
        setFee(o.dunningFee ? o.dunningFee.toFixed(2).replace('.', ',') : '');
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
    const dunningFee = fee.trim() ? parseDecimal(fee) : 0;
    if (!Number.isFinite(dunningFee) || dunningFee < 0) {
      setError(new ApiError('Mahngebühr: bitte einen Betrag wie 5,00 eingeben', 400));
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const res = await api.saveOrganization(token, {
        ...org,
        dunningFee,
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
          <CardTitle>Kontakt</CardTitle>
          <CardDescription>Pflicht für XRechnung: Ansprechpartner, Telefon und E-Mail.</CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          <Field label="Ansprechpartner" value={text(org.contactName)} onChangeText={set('contactName')} />
          <Field label="Telefon" value={text(org.phone)} onChangeText={set('phone')} keyboardType="phone-pad" />
          <Field label="E-Mail" value={text(org.email)} onChangeText={set('email')} autoCapitalize="none" keyboardType="email-address" />
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
          <CardTitle>Mahnwesen</CardTitle>
          <CardDescription>
            Die Zahlungserinnerung ist kostenlos. Ab der 1. Mahnung wird diese Gebühr je Mahnung berechnet.
          </CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          <Field
            label="Mahngebühr je Mahnung (€)"
            value={fee}
            onChangeText={(v) => {
              setSaved(false);
              setFee(v);
            }}
            keyboardType="decimal-pad"
            placeholder="0,00"
          />
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
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>DATEV-Export</CardTitle>
          <CardDescription>
            Buchungsstapel mit Ausgangs- und Eingangsrechnungen für Ihren Steuerberater. Vorher speichern, damit
            Kontenrahmen und Nummern im Export stehen.
          </CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          <View className="flex-row gap-2">
            {[year - 1, year].map((y) => (
              <Chip key={y} label={String(y)} active={exportYear === y} onPress={() => setExportYear(y)} />
            ))}
          </View>
          <ActionButton
            title={`Buchungsstapel ${exportYear} herunterladen`}
            variant="outline"
            disabled={busy}
            onPress={downloadDatev}
          />
        </CardContent>
      </Card>

      <ErrorBox message={error?.message ?? null} errors={error?.errors} />
      {saved && <Text className="font-semibold text-primary">Gespeichert</Text>}
      <ActionButton title="Speichern" onPress={save} busy={busy} />
    </Screen>
  );
}
