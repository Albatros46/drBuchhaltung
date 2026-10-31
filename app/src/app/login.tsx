import { Redirect } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, Text } from 'react-native';

import { Button, Card, colors, ErrorBox, Field, Muted, Screen } from '@/components/ui';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';

export default function LoginScreen() {
  const { session, signIn } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [company, setCompany] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  if (session) return <Redirect href="/" />;

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const res =
        mode === 'login' ? await api.login(email, password) : await api.register(company, email, password);
      await signIn(res);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Screen>
      <Text style={styles.brand}>Rechnio</Text>
      <Muted>Rechnungen, E-Rechnung und DATEV-Export für Ihr Unternehmen.</Muted>
      <Card>
        {mode === 'register' && (
          <Field label="Firmenname" value={company} onChangeText={setCompany} placeholder="Yilmaz Fliesenbau" />
        )}
        <Field
          label="E-Mail"
          value={email}
          onChangeText={setEmail}
          autoCapitalize="none"
          keyboardType="email-address"
          placeholder="name@firma.de"
        />
        <Field
          label="Passwort"
          value={password}
          onChangeText={setPassword}
          secureTextEntry
          placeholder={mode === 'register' ? 'mindestens 8 Zeichen' : ''}
        />
        <ErrorBox message={error?.message ?? null} errors={error?.errors} />
        <Button title={mode === 'login' ? 'Anmelden' : 'Konto erstellen'} onPress={submit} busy={busy} />
        <Pressable onPress={() => setMode(mode === 'login' ? 'register' : 'login')}>
          <Text style={styles.switch}>
            {mode === 'login' ? 'Noch kein Konto? Jetzt registrieren' : 'Schon registriert? Anmelden'}
          </Text>
        </Pressable>
      </Card>
    </Screen>
  );
}

const styles = StyleSheet.create({
  brand: { fontSize: 34, fontWeight: '800', color: colors.ink, marginTop: 40 },
  switch: { color: colors.accent, textAlign: 'center', paddingVertical: 6, fontWeight: '600' },
});
