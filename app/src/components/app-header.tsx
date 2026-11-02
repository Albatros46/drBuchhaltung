import { usePathname, useRouter, type Href } from 'expo-router';
import { Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Text } from '@/components/ui/text';
import { useAuth } from '@/lib/auth';
import { cn } from '@/lib/utils';

/** Hauptbereiche im Menue; die Reihenfolge ist die der Arbeit: schreiben, Kunden, Eingang, Export. */
export const NAV_ITEMS: { href: Href & string; label: string }[] = [
  { href: '/', label: 'Übersicht' },
  { href: '/invoices/new', label: '+ Neue Rechnung' },
  { href: '/customers', label: 'Kunden' },
  { href: '/incoming', label: 'Eingangsrechnungen' },
  { href: '/export', label: 'Export' },
];

const TOP_LEVEL = new Set<string>([...NAV_ITEMS.map((i) => i.href), '/settings']);

/**
 * Banner mit Menue fuer alle angemeldeten Bildschirme. Der Firmenname oeffnet die Firmendaten.
 * Unterseiten (z. B. ein Beleg) zeigen darunter Titel und Zurueck.
 */
export function AppHeader({ title, onBack }: { title?: string; onBack?: () => void }) {
  const { session, signOut } = useAuth();
  const pathname = usePathname();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  // Menuepunkte sind Hauptbereiche: zurueck zur Uebersicht und von dort oeffnen, statt den Verlauf zu stapeln.
  const go = (href: Href & string) => {
    if (pathname === href) return;
    if (router.canDismiss()) router.dismissAll();
    if (href !== '/') router.push(href);
  };

  const isDetail = !TOP_LEVEL.has(pathname);

  return (
    <View>
      <View className="bg-primary" style={{ paddingTop: insets.top }}>
        <View className="w-full max-w-5xl flex-row items-center justify-between gap-3 self-center px-4 py-3">
          <Pressable accessibilityRole="link" onPress={() => go('/')}>
            <Text className="text-xl font-bold text-primary-foreground">Rechnio</Text>
          </Pressable>
          <View className="flex-shrink flex-row items-center gap-1">
            <Pressable
              accessibilityRole="link"
              accessibilityLabel="Firmendaten öffnen"
              onPress={() => go('/settings')}
              className={cn('rounded-md px-3 py-1.5', pathname === '/settings' ? 'bg-white/20' : 'active:bg-white/10')}>
              <Text numberOfLines={1} className="font-semibold text-primary-foreground">
                {session?.companyName ?? 'Firma'}
              </Text>
            </Pressable>
            <Pressable accessibilityRole="button" onPress={signOut} className="rounded-md px-3 py-1.5 active:bg-white/10">
              <Text className="text-primary-foreground/80">Abmelden</Text>
            </Pressable>
          </View>
        </View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerClassName="w-full">
          <View className="w-full max-w-5xl flex-row gap-1 self-center px-2">
            {NAV_ITEMS.map((item) => {
              const active = pathname === item.href;
              return (
                <Pressable
                  key={item.href}
                  accessibilityRole="tab"
                  accessibilityState={{ selected: active }}
                  onPress={() => go(item.href)}
                  className={cn('border-b-4 px-3 pb-2 pt-1', active ? 'border-white' : 'border-transparent active:bg-white/10')}>
                  <Text className={cn('text-primary-foreground', active ? 'font-semibold' : 'opacity-80')}>
                    {item.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        </ScrollView>
      </View>
      {title ? (
        <View className="border-b border-border bg-card">
          <View className="w-full max-w-2xl flex-row items-center gap-2 self-center px-4 py-3">
            {isDetail && onBack ? (
              <Pressable accessibilityRole="button" accessibilityLabel="Zurück" onPress={onBack} className="-ml-2 rounded-md px-2 py-1 active:bg-muted">
                <Text className="text-lg">←</Text>
              </Pressable>
            ) : null}
            <Text className="text-lg font-semibold">{title}</Text>
          </View>
        </View>
      ) : null}
    </View>
  );
}
