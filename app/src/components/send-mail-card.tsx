import { useState } from 'react';
import { View } from 'react-native';

import { ActionButton, Chip, ErrorBox, Field } from '@/components/app-ui';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Text } from '@/components/ui/text';
import { api, ApiError, Invoice, InvoiceMail, MailAttachment } from '@/lib/api';

const ATTACHMENTS: { value: MailAttachment; label: string }[] = [
  { value: 'ZUGFERD', label: 'ZUGFeRD-PDF' },
  { value: 'PDF', label: 'PDF' },
  { value: 'XRECHNUNG', label: 'XRechnung' },
];

/** Versand eines ausgestellten Belegs per E-Mail; der Vorschlag kommt vom Backend. */
export function SendMailCard({ token, inv, onSent }: { token: string; inv: Invoice; onSent: (i: Invoice) => void }) {
  const [mail, setMail] = useState<InvoiceMail | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [sentInfo, setSentInfo] = useState<string | null>(null);

  const open = async () => {
    setError(null);
    setSentInfo(null);
    setBusy(true);
    try {
      setMail(await api.emailDraft(token, inv.id));
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  const send = async () => {
    if (!mail) return;
    setError(null);
    setBusy(true);
    try {
      const updated = await api.sendInvoice(token, inv.id, mail);
      setMail(null);
      setSentInfo(`Gesendet an ${updated.sentTo}`);
      onSent(updated);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  const set = (patch: Partial<InvoiceMail>) => setMail((m) => (m ? { ...m, ...patch } : m));
  const options = inv.docType === 'ANGEBOT' ? ATTACHMENTS.filter((a) => a.value === 'PDF') : ATTACHMENTS;

  return (
    <Card>
      <CardHeader>
        <CardTitle>Per E-Mail senden</CardTitle>
        <CardDescription>
          {inv.sentAt
            ? `Zuletzt gesendet am ${new Date(inv.sentAt).toLocaleString('de-DE')} an ${inv.sentTo}`
            : 'Noch nicht verschickt.'}
        </CardDescription>
      </CardHeader>
      <CardContent className="gap-3">
        {sentInfo && <Text className="font-semibold text-primary">{sentInfo}</Text>}
        {!mail ? (
          <ActionButton title={inv.sentAt ? 'Erneut senden' : 'E-Mail vorbereiten'} variant="outline" busy={busy} onPress={open} />
        ) : (
          <>
            <Field
              label="An (mehrere durch Komma trennen)"
              value={mail.to}
              onChangeText={(to) => set({ to })}
              autoCapitalize="none"
              keyboardType="email-address"
              placeholder="kunde@firma.de"
            />
            <Field label="Betreff" value={mail.subject} onChangeText={(subject) => set({ subject })} />
            <Field
              label="Nachricht"
              value={mail.message}
              onChangeText={(message) => set({ message })}
              multiline
              numberOfLines={10}
              className="flex-none"
              inputClassName="h-56 items-start py-2 sm:h-56"
              textAlignVertical="top"
            />
            <View className="gap-1.5">
              <Text variant="small">Anhang</Text>
              <View className="flex-row flex-wrap gap-2">
                {options.map((o) => (
                  <Chip key={o.value} label={o.label} active={mail.attachment === o.value} onPress={() => set({ attachment: o.value })} />
                ))}
              </View>
            </View>
            <View className="flex-row gap-2">
              <ActionButton title="Senden" className="flex-1" busy={busy} onPress={send} />
              <ActionButton title="Abbrechen" variant="outline" className="flex-1" disabled={busy} onPress={() => setMail(null)} />
            </View>
          </>
        )}
        <ErrorBox message={error?.message ?? null} errors={error?.errors} />
      </CardContent>
    </Card>
  );
}
