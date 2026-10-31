import { ReactNode } from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  View,
  ViewStyle,
} from 'react-native';

import { InvoiceStatus } from '@/lib/api';
import { statusLabel } from '@/lib/format';

export const colors = {
  bg: '#eef1f4',
  card: '#ffffff',
  ink: '#16233b',
  muted: '#5b6678',
  line: '#c9d0da',
  accent: '#0d7a6a',
  accentSoft: '#d6eee9',
  danger: '#b3261e',
};

export function Screen({ children }: { children: ReactNode }) {
  return (
    <ScrollView style={styles.screen} contentContainerStyle={styles.screenContent}>
      <View style={styles.inner}>{children}</View>
    </ScrollView>
  );
}

export function Card({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.card, style]}>{children}</View>;
}

export function H2({ children }: { children: ReactNode }) {
  return <Text style={styles.h2}>{children}</Text>;
}

export function Muted({ children }: { children: ReactNode }) {
  return <Text style={styles.muted}>{children}</Text>;
}

export function Field({ label, ...props }: TextInputProps & { label: string }) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      <TextInput style={styles.input} placeholderTextColor={colors.muted} {...props} />
    </View>
  );
}

export function Button({
  title,
  onPress,
  variant = 'primary',
  busy,
  disabled,
}: {
  title: string;
  onPress: () => void;
  variant?: 'primary' | 'ghost' | 'danger';
  busy?: boolean;
  disabled?: boolean;
}) {
  const ghost = variant !== 'primary';
  const tint = variant === 'danger' ? colors.danger : colors.accent;
  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      disabled={busy || disabled}
      style={({ pressed }) => [
        styles.button,
        ghost ? { borderColor: tint, borderWidth: 1.5 } : { backgroundColor: tint },
        (pressed || disabled) && { opacity: 0.6 },
      ]}>
      {busy ? (
        <ActivityIndicator color={ghost ? tint : '#fff'} />
      ) : (
        <Text style={[styles.buttonText, { color: ghost ? tint : '#fff' }]}>{title}</Text>
      )}
    </Pressable>
  );
}

export function StatusPill({ status }: { status: InvoiceStatus }) {
  const color =
    status === 'PAID' ? colors.accent : status === 'ISSUED' ? colors.danger : colors.muted;
  return <Text style={[styles.pill, { color, borderColor: color }]}>{statusLabel[status]}</Text>;
}

export function ErrorBox({ message, errors = [] }: { message: string | null; errors?: string[] }) {
  if (!message) return null;
  return (
    <View style={styles.error}>
      <Text style={styles.errorTitle}>{message}</Text>
      {errors.map((e) => (
        <Text key={e} style={styles.errorItem}>
          • {e}
        </Text>
      ))}
    </View>
  );
}

export function Row({ children, style }: { children: ReactNode; style?: ViewStyle }) {
  return <View style={[styles.row, style]}>{children}</View>;
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: colors.bg },
  screenContent: { padding: 16, paddingBottom: 48 },
  inner: { width: '100%', maxWidth: 720, alignSelf: 'center', gap: 12 },
  card: {
    backgroundColor: colors.card,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: colors.line,
    padding: 14,
    gap: 8,
  },
  h2: { fontSize: 18, fontWeight: '700', color: colors.ink },
  muted: { color: colors.muted },
  field: { gap: 4, flex: 1 },
  label: { fontSize: 12, color: colors.muted, fontWeight: '600' },
  input: {
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 6,
    paddingHorizontal: 10,
    paddingVertical: 8,
    backgroundColor: colors.card,
    color: colors.ink,
    fontSize: 15,
  },
  button: {
    borderRadius: 8,
    paddingVertical: 11,
    paddingHorizontal: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonText: { fontWeight: '700', fontSize: 15 },
  pill: {
    fontSize: 11,
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 8,
    paddingVertical: 1,
    overflow: 'hidden',
  },
  error: { backgroundColor: '#fdecea', borderRadius: 6, padding: 10, gap: 2 },
  errorTitle: { color: colors.danger, fontWeight: '700' },
  errorItem: { color: colors.danger },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8 },
});
