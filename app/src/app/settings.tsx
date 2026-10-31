import { useEffect, useState } from 'react';
import { Pressable, StyleSheet, Switch, Text } from 'react-native';

import { Button, Card, colors, ErrorBox, Field, H2, Muted, Row, Screen } from '@/components/ui';
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

  if (!org) return <Screen><ErrorBox message={error?.message ?? null} /></Screen>;

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
        <H2>Firma (Pflichtangaben § 14 UStG)</H2>
        <Field label="Firmenname" value={org.name} onChangeText={(v) => setOrg({ ...org, name: v })} />
        <Field label="Straße und Hausnummer" value={text(org.street)} onChangeText={set('street')} />
        <Row>
          <Field label="PLZ" value={text(org.zip)} onChangeText={set('zip')} keyboardType="number-pad" />
          <Field label="Ort" value={text(org.city)} onChangeText={set('city')} />
        </Row>
        <Field label="Steuernummer" value={text(org.steuernummer)} onChangeText={set('steuernummer')} placeholder="12/345/67890" />
        <Field label="USt-IdNr." value={text(org.ustId)} onChangeText={set('ustId')} autoCapitalize="characters" />
        <Row>
          <Text style={styles.ink}>Kleinunternehmer (§ 19 UStG)</Text>
          <Switch value={org.kleinunternehmer} onValueChange={(v) => setOrg({ ...org, kleinunternehmer: v })} />
        </Row>
      </Card>

      <Card>
        <H2>Bank</H2>
        <Field label="IBAN" value={text(org.iban)} onChangeText={set('iban')} autoCapitalize="characters" />
        <Field label="BIC" value={text(org.bic)} onChangeText={set('bic')} autoCapitalize="characters" />
      </Card>

      <Card>
        <H2>DATEV</H2>
        <Row style={{ justifyContent: 'flex-start' }}>
          {(['SKR03', 'SKR04'] as const).map((k) => (
            <Pressable key={k} onPress={() => setOrg({ ...org, kontenrahmen: k })} style={[styles.chip, org.kontenrahmen === k && styles.chipActive]}>
              <Text style={{ color: org.kontenrahmen === k ? colors.accent : colors.ink }}>{k}</Text>
            </Pressable>
          ))}
        </Row>
        <Row>
          <Field label="Beraternummer" value={berater} onChangeText={setBerater} keyboardType="number-pad" />
          <Field label="Mandantennummer" value={mandant} onChangeText={setMandant} keyboardType="number-pad" />
        </Row>
        <Muted>
          Export für {year}: GET {API_URL}/api/exports/datev?from={year}-01-01&to={year}-12-31
        </Muted>
      </Card>

      <ErrorBox message={error?.message ?? null} errors={error?.errors} />
      {saved && <Text style={{ color: colors.accent, fontWeight: '700' }}>Gespeichert</Text>}
      <Button title="Speichern" onPress={save} busy={busy} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  ink: { color: colors.ink },
  chip: { borderWidth: 1, borderColor: colors.line, borderRadius: 999, paddingHorizontal: 12, paddingVertical: 6 },
  chipActive: { borderColor: colors.accent, backgroundColor: colors.accentSoft },
});
