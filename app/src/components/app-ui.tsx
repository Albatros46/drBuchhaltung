import { ReactNode } from 'react';
import { ActivityIndicator, Pressable, ScrollView, TextInputProps, View } from 'react-native';

import { Badge } from '@/components/ui/badge';
import { Button, type ButtonProps } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Text } from '@/components/ui/text';
import { InvoiceStatus } from '@/lib/api';
import { statusLabel } from '@/lib/format';
import { cn } from '@/lib/utils';

/** Scrollbarer Bildschirm mit einheitlichem Rand und maximaler Breite fuer Web und Tablet. */
export function Screen({ children }: { children: ReactNode }) {
  return (
    <ScrollView className="flex-1 bg-background" contentContainerClassName="p-4 pb-12">
      <View className="w-full max-w-2xl gap-3 self-center">{children}</View>
    </ScrollView>
  );
}

/** Beschriftetes Eingabefeld; id verbindet Label und Input fuer Screenreader. */
export function Field({ label, nativeID, className, ...props }: TextInputProps & { label: string; className?: string }) {
  const id = nativeID ?? label.replace(/\W+/g, '-').toLowerCase();
  return (
    <View className={cn('flex-1 gap-1.5', className)}>
      <Label nativeID={id}>{label}</Label>
      <Input aria-labelledby={id} accessibilityLabel={label} {...props} />
    </View>
  );
}

/** Button mit Ladezustand; der Text wird ueber Text-Kontext passend zur Variante eingefaerbt. */
export function ActionButton({ title, busy, disabled, ...props }: ButtonProps & { title: string; busy?: boolean }) {
  return (
    <Button disabled={busy || disabled} {...props}>
      {busy ? <ActivityIndicator /> : <Text>{title}</Text>}
    </Button>
  );
}

export function StatusPill({ status }: { status: InvoiceStatus }) {
  const variant = status === 'PAID' ? 'default' : status === 'ISSUED' ? 'destructive' : 'outline';
  return (
    <Badge variant={variant}>
      <Text>{statusLabel[status]}</Text>
    </Badge>
  );
}

export function ErrorBox({ message, errors = [] }: { message: string | null; errors?: string[] }) {
  if (!message) return null;
  return (
    <View className="gap-0.5 rounded-md border border-destructive/40 bg-destructive/10 p-3">
      <Text className="font-semibold text-destructive">{message}</Text>
      {errors.map((e) => (
        <Text key={e} className="text-destructive">
          • {e}
        </Text>
      ))}
    </View>
  );
}

/** Auswahl-Chip fuer kleine, feste Optionen (Belegart, Kunde, Steuersatz). */
export function Chip({ label, active, onPress }: { label: string; active: boolean; onPress: () => void }) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      onPress={onPress}
      className={cn(
        'rounded-full border px-3 py-1.5',
        active ? 'border-primary bg-secondary' : 'border-border bg-card',
      )}>
      <Text className={cn('text-sm', active ? 'font-semibold text-primary' : 'text-foreground')}>{label}</Text>
    </Pressable>
  );
}

/** Zeile Bezeichnung / Betrag mit tabellarischen Ziffern. */
export function AmountRow({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <View className="flex-row items-center justify-between">
      <Text className={cn(strong && 'text-base font-bold')}>{label}</Text>
      <Text className={cn('tabular-nums', strong && 'text-base font-bold')}>{value}</Text>
    </View>
  );
}
