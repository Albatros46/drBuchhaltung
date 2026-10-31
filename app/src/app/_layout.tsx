import '../../global.css';

import { PortalHost } from '@rn-primitives/portal';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

import { colors } from '@/components/ui';
import { AuthProvider } from '@/lib/auth';

export default function RootLayout() {
  return (
    <AuthProvider>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: colors.card },
          headerTintColor: colors.ink,
          contentStyle: { backgroundColor: colors.bg },
        }}>
        <Stack.Screen name="index" options={{ title: 'Übersicht' }} />
        <Stack.Screen name="login" options={{ title: 'Anmelden', headerShown: false }} />
        <Stack.Screen name="invoices/new" options={{ title: 'Neue Rechnung' }} />
        <Stack.Screen name="invoices/[id]" options={{ title: 'Beleg' }} />
        <Stack.Screen name="customers" options={{ title: 'Kunden' }} />
        <Stack.Screen name="settings" options={{ title: 'Firma' }} />
      </Stack>
      <PortalHost />
    </AuthProvider>
  );
}
