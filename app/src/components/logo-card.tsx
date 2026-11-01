import * as DocumentPicker from 'expo-document-picker';
import { useEffect, useState } from 'react';
import { Image, View } from 'react-native';

import { ActionButton, ErrorBox } from '@/components/app-ui';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Text } from '@/components/ui/text';
import { api, ApiError, Logo } from '@/lib/api';

/** Firmenlogo fuer den Briefkopf von Rechnungen und Mahnungen. */
export function LogoCard({ token }: { token: string }) {
  const [logo, setLogo] = useState<Logo | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    api.logo(token).then(setLogo).catch((e: ApiError) => setError(e));
  }, [token]);

  const pick = async () => {
    setError(null);
    const result = await DocumentPicker.getDocumentAsync({ type: ['image/png', 'image/jpeg'], copyToCacheDirectory: true });
    if (result.canceled) return;
    setBusy(true);
    try {
      setLogo(await api.uploadLogo(token, result.assets[0]));
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    setError(null);
    setBusy(true);
    try {
      await api.deleteLogo(token);
      setLogo(null);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Logo</CardTitle>
        <CardDescription>
          Erscheint oben rechts auf Rechnungen, Angeboten und Mahnungen. PNG oder JPEG, höchstens 1 MB.
        </CardDescription>
      </CardHeader>
      <CardContent className="gap-3">
        {logo ? (
          <View className="items-start rounded-md border border-border bg-card p-3">
            <Image
              source={{ uri: logo.dataUri }}
              accessibilityLabel="Firmenlogo"
              resizeMode="contain"
              style={{ width: 240, height: Math.min(80, (240 * logo.height) / logo.width) }}
            />
          </View>
        ) : logo === null ? (
          <Text variant="muted">Noch kein Logo hinterlegt.</Text>
        ) : null}
        <View className="flex-row gap-2">
          <ActionButton
            title={logo ? 'Logo ersetzen' : 'Logo hochladen'}
            variant="outline"
            className="flex-1"
            busy={busy}
            onPress={pick}
          />
          {logo && <ActionButton title="Entfernen" variant="outline" className="flex-1" disabled={busy} onPress={remove} />}
        </View>
        <ErrorBox message={error?.message ?? null} errors={error?.errors} />
      </CardContent>
    </Card>
  );
}
