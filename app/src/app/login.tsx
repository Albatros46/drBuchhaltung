import { useState } from 'react';
import { Pressable } from 'react-native';

import { ActionButton, ErrorBox, Field, Screen } from '@/components/app-ui';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Text } from '@/components/ui/text';
import { api, ApiError } from '@/lib/api';
import { useAuth } from '@/lib/auth';

export default function LoginScreen() {
  const { signIn } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [company, setCompany] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

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
      <Text variant="h1" className="mt-10 text-left">
        Rechnio
      </Text>
      <Text variant="muted">Rechnungen, E-Rechnung und DATEV-Export für Ihr Unternehmen.</Text>
      <Card>
        <CardHeader>
          <CardTitle>{mode === 'login' ? 'Anmelden' : 'Konto erstellen'}</CardTitle>
          <CardDescription>
            {mode === 'login' ? 'Mit E-Mail und Passwort anmelden.' : 'Firma und ersten Benutzer anlegen.'}
          </CardDescription>
        </CardHeader>
        <CardContent className="gap-4">
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
          <ActionButton title={mode === 'login' ? 'Anmelden' : 'Konto erstellen'} onPress={submit} busy={busy} />
          <Pressable onPress={() => setMode(mode === 'login' ? 'register' : 'login')}>
            <Text className="py-1 text-center font-semibold text-primary">
              {mode === 'login' ? 'Noch kein Konto? Jetzt registrieren' : 'Schon registriert? Anmelden'}
            </Text>
          </Pressable>
        </CardContent>
      </Card>
    </Screen>
  );
}
