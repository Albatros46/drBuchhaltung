import { useCallback, useEffect, useState } from 'react';
import { View } from 'react-native';

import { ActionButton, AmountRow, ErrorBox } from '@/components/app-ui';
import { SendMailCard } from '@/components/send-mail-card';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Text } from '@/components/ui/text';
import { api, ApiError, Dunning, DunningLevel, Invoice } from '@/lib/api';
import { openDocument } from '@/lib/download';
import { date, money } from '@/lib/format';

const NEXT_TITLE: Record<DunningLevel | 'NONE', string | null> = {
  NONE: 'Zahlungserinnerung',
  ZAHLUNGSERINNERUNG: '1. Mahnung',
  MAHNUNG_1: '2. Mahnung',
  MAHNUNG_2: null,
};

/** Mahnwesen einer Rechnung: bisherige Stufen, naechste Stufe anlegen, PDF und E-Mail je Stufe. */
export function DunningCard({ token, inv }: { token: string; inv: Invoice }) {
  const [items, setItems] = useState<Dunning[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [mailFor, setMailFor] = useState<string | null>(null);

  const load = useCallback(() => {
    api.dunnings(token, inv.id).then(setItems).catch((e: ApiError) => setError(e));
  }, [token, inv.id]);
  useEffect(load, [load]);

  const today = new Date().toISOString().slice(0, 10);
  const overdue = !!inv.dueDate && inv.dueDate < today;
  const open = inv.status === 'ISSUED';
  // Nichts anzeigen, solange es nichts zu mahnen gibt und noch keine Mahnung existiert
  if (items === null || (items.length === 0 && !(open && overdue))) return null;

  const last = items.at(-1);
  const nextTitle = NEXT_TITLE[last?.level ?? 'NONE'];
  const deadlineRunning = !!last && last.deadline >= today;

  const create = async () => {
    setBusy(true);
    setError(null);
    try {
      const created = await api.createDunning(token, inv.id);
      setItems((prev) => [...(prev ?? []), created]);
      setMailFor(created.id);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="border-destructive/40">
      <CardHeader>
        <CardTitle>Mahnwesen</CardTitle>
        <CardDescription>
          {open ? `Überfällig seit ${date(inv.dueDate)}.` : 'Die Rechnung ist nicht mehr offen.'}
        </CardDescription>
      </CardHeader>
      <CardContent className="gap-3">
        {items.map((d, i) => (
          <View key={d.id} className="gap-2">
            {i > 0 && <Separator />}
            <View className="flex-row items-center justify-between gap-2">
              <Text className="font-semibold">{d.title}</Text>
              <Badge variant={d.sentAt ? 'default' : 'outline'}>
                <Text>{d.sentAt ? 'versendet' : 'nicht versendet'}</Text>
              </Badge>
            </View>
            <Text variant="muted">
              vom {date(d.issueDate)} · zahlbar bis {date(d.deadline)}
            </Text>
            {d.fee > 0 && <AmountRow label="Mahngebühr" value={money(d.fee)} />}
            <AmountRow label="Offen inkl. Gebühren" value={money(d.totalDue)} strong />
            <View className="flex-row gap-2">
              <ActionButton
                title="PDF"
                variant="outline"
                className="flex-1"
                onPress={() =>
                  openDocument(token, `/api/dunnings/${d.id}/pdf`, `${d.title.replace('. ', '-')}-${inv.number}.pdf`, 'application/pdf').catch(
                    (e: ApiError) => setError(e),
                  )
                }
              />
              <ActionButton
                title={mailFor === d.id ? 'E-Mail ausblenden' : 'Per E-Mail'}
                variant="outline"
                className="flex-1"
                onPress={() => setMailFor(mailFor === d.id ? null : d.id)}
              />
            </View>
            {mailFor === d.id && (
              <SendMailCard
                embedded
                sentAt={d.sentAt}
                sentTo={d.sentTo}
                loadDraft={() => api.dunningEmailDraft(token, d.id)}
                send={async (mail) => {
                  const updated = await api.sendDunning(token, d.id, mail);
                  setItems((prev) => (prev ?? []).map((x) => (x.id === updated.id ? updated : x)));
                  return updated;
                }}
              />
            )}
          </View>
        ))}

        {open && nextTitle && (
          <>
            {items.length > 0 && <Separator />}
            {deadlineRunning ? (
              <Text variant="muted">
                {nextTitle} möglich nach Ablauf der Frist am {date(last!.deadline)}.
              </Text>
            ) : (
              <ActionButton title={`${nextTitle} erstellen`} busy={busy} onPress={create} />
            )}
          </>
        )}
        {open && !nextTitle && (
          <Text variant="muted">Höchste Mahnstufe erreicht. Weitere Schritte bitte außerhalb der App veranlassen.</Text>
        )}
        <ErrorBox message={error?.message ?? null} errors={error?.errors} />
      </CardContent>
    </Card>
  );
}
