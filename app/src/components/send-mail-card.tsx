import { useState } from 'react';
import { View } from 'react-native';

import { ActionButton, Chip, ErrorBox, Field } from '@/components/app-ui';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Text } from '@/components/ui/text';
import { ApiError, MailAttachment, MailDraft } from '@/lib/api';

export const ATTACHMENT_LABEL: Record<MailAttachment, string> = {
  ZUGFERD: 'ZUGFeRD-PDF',
  PDF: 'PDF',
  XRECHNUNG: 'XRechnung',
};

interface Props {
  title?: string;
  sentAt: string | null;
  sentTo: string | null;
  /** Vorschlag vom Backend (Empfaenger, Betreff, Text, ggf. Anhang). */
  loadDraft: () => Promise<MailDraft>;
  /** Sendet und liefert den neuen Versandvermerk. */
  send: (mail: MailDraft) => Promise<{ sentAt: string | null; sentTo: string | null }>;
  /** Waehlbare Anhaenge; ohne Angabe wird keine Auswahl angezeigt. */
  attachmentOptions?: MailAttachment[];
  /** Ohne eigene Karte, z. B. innerhalb der Mahnwesen-Karte. */
  embedded?: boolean;
}

/** E-Mail-Versand eines Belegs: Vorschlag laden, bearbeiten, senden. */
export function SendMailCard({ title = 'Per E-Mail senden', sentAt, sentTo, loadDraft, send, attachmentOptions, embedded }: Props) {
  const [mail, setMail] = useState<MailDraft | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [sentInfo, setSentInfo] = useState<string | null>(null);

  const open = async () => {
    setError(null);
    setSentInfo(null);
    setBusy(true);
    try {
      setMail(await loadDraft());
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  const submit = async () => {
    if (!mail) return;
    setError(null);
    setBusy(true);
    try {
      const result = await send(mail);
      setMail(null);
      setSentInfo(`Gesendet an ${result.sentTo}`);
    } catch (e) {
      setError(e as ApiError);
    } finally {
      setBusy(false);
    }
  };

  const set = (patch: Partial<MailDraft>) => setMail((m) => (m ? { ...m, ...patch } : m));
  const status = sentAt
    ? `Zuletzt gesendet am ${new Date(sentAt).toLocaleString('de-DE')} an ${sentTo}`
    : 'Noch nicht verschickt.';

  const body = (
    <View className="gap-3">
      {sentInfo && <Text className="font-semibold text-primary">{sentInfo}</Text>}
      {!mail ? (
        <ActionButton title={sentAt ? 'Erneut senden' : 'E-Mail vorbereiten'} variant="outline" busy={busy} onPress={open} />
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
          {attachmentOptions && attachmentOptions.length > 0 && (
            <View className="gap-1.5">
              <Text variant="small">Anhang</Text>
              <View className="flex-row flex-wrap gap-2">
                {attachmentOptions.map((o) => (
                  <Chip key={o} label={ATTACHMENT_LABEL[o]} active={mail.attachment === o} onPress={() => set({ attachment: o })} />
                ))}
              </View>
            </View>
          )}
          <View className="flex-row gap-2">
            <ActionButton title="Senden" className="flex-1" busy={busy} onPress={submit} />
            <ActionButton title="Abbrechen" variant="outline" className="flex-1" disabled={busy} onPress={() => setMail(null)} />
          </View>
        </>
      )}
      <ErrorBox message={error?.message ?? null} errors={error?.errors} />
    </View>
  );

  if (embedded) {
    return (
      <View className="gap-2">
        <Text variant="muted">{status}</Text>
        {body}
      </View>
    );
  }
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{status}</CardDescription>
      </CardHeader>
      <CardContent>{body}</CardContent>
    </Card>
  );
}
