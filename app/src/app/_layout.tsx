import '../../global.css';

import { PortalHost } from '@rn-primitives/portal';
import { Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { ActivityIndicator, View } from 'react-native';

import { AuthProvider, useAuth } from '@/lib/auth';
import { NAV_THEME } from '@/lib/theme';

export default function RootLayout() {
  return (
    <ThemeProvider value={NAV_THEME.light}>
      <AuthProvider>
        <StatusBar style="dark" />
        <RootNavigator />
        <PortalHost />
      </AuthProvider>
    </ThemeProvider>
  );
}

function RootNavigator() {
  const { session, loading } = useAuth();

  // Erst rendern, wenn die gespeicherte Sitzung gelesen ist: sonst laufen API-Aufrufe
  // nach einem Neuladen der Seite mit leerem Token und enden mit 401.
  if (loading) {
    return (
      <View className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator />
      </View>
    );
  }

  return (
    <Stack>
      <Stack.Protected guard={!!session}>
        <Stack.Screen name="index" options={{ title: 'Übersicht' }} />
        <Stack.Screen name="invoices/new" options={{ title: 'Neue Rechnung' }} />
        <Stack.Screen name="invoices/[id]" options={{ title: 'Beleg' }} />
        <Stack.Screen name="customers" options={{ title: 'Kunden' }} />
        <Stack.Screen name="settings" options={{ title: 'Firma' }} />
      </Stack.Protected>
      <Stack.Protected guard={!session}>
        <Stack.Screen name="login" options={{ title: 'Anmelden', headerShown: false }} />
      </Stack.Protected>
    </Stack>
  );
}
