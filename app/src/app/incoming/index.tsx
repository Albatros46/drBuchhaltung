import * as DocumentPicker from 'expo-document-picker';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';

import { ActionButton, ErrorBox, Screen } from '@/components/app-ui';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Text } from '@/components/ui/text';
import { api, ApiError, IncomingInvoice } from '@/lib/api';
import { useToken } from '@/lib/auth';
import { date, money } from '@/lib/format';
import { formatLabel } from '@/lib/incoming';

export default function IncomingInvoicesScreen() {
  const token = useToken();
  const router = useRouter();
  const [items, setItems] = useState<IncomingInvoice[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  const load = useCallback(() => {
    api.incomingInvoices(token).then(setItems).catch((e: ApiError) => setError(e));
  }, [token]);
  useFocusEffect(load);

  const pickAndUpload = async () => {
    setError(null);
    const result = await DocumentPicker.getDocumentAsync({
      type: ['application/pdf', 'application/xml', 'text/xml'],
      copyToCacheDirectory: true,
    });
    if (result.canceled) return;
    const asset = result.assets[0];
    setBusy(true);
    try {
      const created = await api.uploadIncoming(token, asset);
      router.push(`/incoming/${created.id}`);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  const open = (items ?? []).filter((i) => i.status === 'OPEN');

  return (
    <Screen>
      <Card>
        <CardHeader>
          <CardTitle>E-Rechnung empfangen</CardTitle>
          <CardDescription>
            ZUGFeRD-PDF oder XRechnung (XML) hochladen. Die Datei wird geprüft und unverändert archiviert.
          </CardDescription>
        </CardHeader>
        <CardContent className="gap-3">
          <ActionButton title="Datei auswählen und einlesen" onPress={pickAndUpload} busy={busy} />
          <ErrorBox message={error?.message ?? null} errors={error?.errors} />
        </CardContent>
      </Card>

      {items !== null && items.length > 0 && (
        <Text variant="muted">
          {open.length} offen · zu zahlen {money(open.reduce((s, i) => s + i.grossTotal, 0))}
        </Text>
      )}

      {items === null && !error && <ActivityIndicator />}
      {items?.length === 0 && (
        <Card>
          <CardContent>
            <Text variant="muted">Noch keine Eingangsrechnungen. Laden Sie die erste E-Rechnung eines Lieferanten hoch.</Text>
          </CardContent>
        </Card>
      )}

      {items?.map((inv) => (
        <Pressable key={inv.id} onPress={() => router.push(`/incoming/${inv.id}`)}>
          <Card className="py-4">
            <CardContent className="gap-1">
              <View className="flex-row items-center justify-between gap-2">
                <Text className="flex-1 font-semibold" numberOfLines={1}>
                  {inv.supplierName ?? 'Unbekannter Lieferant'}
                </Text>
                <Badge variant={inv.status === 'PAID' ? 'default' : 'outline'}>
                  <Text>{inv.status === 'PAID' ? 'bezahlt' : 'offen'}</Text>
                </Badge>
              </View>
              <View className="flex-row items-center justify-between">
                <Text variant="muted">
                  {inv.documentType === 'GUTSCHRIFT' ? 'Gutschrift' : 'Rechnung'} {inv.number ?? ''} · {date(inv.issueDate)}
                </Text>
                <Text className="tabular-nums">{money(inv.grossTotal)}</Text>
              </View>
              <View className="flex-row gap-2">
                <Badge variant="secondary">
                  <Text>{formatLabel[inv.format]}</Text>
                </Badge>
                {inv.validationStatus === 'INVALID' && (
                  <Badge variant="destructive">
                    <Text>Prüfung: Fehler</Text>
                  </Badge>
                )}
              </View>
            </CardContent>
          </Card>
        </Pressable>
      ))}
    </Screen>
  );
}
