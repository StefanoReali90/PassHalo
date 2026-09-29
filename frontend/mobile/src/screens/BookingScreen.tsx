import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Image,
  ImageBackground,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';
import { CalendarDays, MapPin, TicketCheck } from 'lucide-react-native';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import { isEventBookable } from '../eventAvailability';
import { colors, radii, spacing } from '../theme';
import type { BookingResponse, PassHaloEvent } from '../types';
import { Button, Card, Field, LoadingBlock, Notice, PageHeader } from '../components/ui';

const money = (value: number) => value.toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
const date = (value: string) => new Intl.DateTimeFormat('it-IT', {
  dateStyle: 'medium',
  timeStyle: 'short',
}).format(new Date(value));

function qrSource(value: string) {
  return value.startsWith('data:') ? value : `data:image/png;base64,${value}`;
}

export function BookingScreen({ onOpenSettings }: { onOpenSettings(): void }) {
  const { apiBaseUrl } = useAuth();
  const [events, setEvents] = useState<PassHaloEvent[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<BookingResponse | null>(null);
  const [name, setName] = useState('');
  const [surname, setSurname] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [marketingConsent, setMarketingConsent] = useState(false);
  const [privacyAcknowledged, setPrivacyAcknowledged] = useState(false);

  const loadEvents = useCallback(async () => {
    if (!apiBaseUrl) return;
    setLoading(true);
    setError('');
    try {
      const available = (await api.events()).filter(isEventBookable);
      setEvents(available);
      setSelectedId((current) => available.some((event) => event.id === current) ? current : available[0]?.id ?? null);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Eventi non disponibili.');
    } finally {
      setLoading(false);
    }
  }, [apiBaseUrl]);

  useEffect(() => {
    void loadEvents();
  }, [loadEvents]);

  const selectedEvent = useMemo(
    () => events.find((event) => event.id === selectedId),
    [events, selectedId],
  );

  useEffect(() => {
    setMarketingConsent(false);
    setPrivacyAcknowledged(false);
  }, [selectedId]);

  const submit = async () => {
    if (!selectedEvent || !name.trim() || !surname.trim() || !email.trim()) {
      setError('Seleziona un evento e compila nome, cognome ed email.');
      return;
    }
    if (!isEventBookable(selectedEvent)) {
      setError('Le prenotazioni per questo evento sono chiuse.');
      return;
    }
    if (!privacyAcknowledged) {
      setError('Leggi e conferma l’informativa privacy prima di prenotare.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      const created = await api.createBooking({
        name: name.trim(),
        surname: surname.trim(),
        email: email.trim(),
        phone: phone.trim(),
        eventId: selectedEvent.id,
        marketingConsent,
      });
      setResult(created);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Prenotazione non riuscita.');
    } finally {
      setSubmitting(false);
    }
  };

  const reset = () => {
    setResult(null);
    setName('');
    setSurname('');
    setEmail('');
    setPhone('');
    setMarketingConsent(false);
    setPrivacyAcknowledged(false);
  };

  if (!apiBaseUrl) {
    return (
      <ScrollView contentContainerStyle={styles.page}>
        <PageHeader eyebrow="PassHalo mobile" title="Prenota il tuo ingresso." description="Collega prima l’app al server usato per i test." />
        <Notice tone="error">Il server PassHalo non è ancora configurato.</Notice>
        <Button label="Configura il server" onPress={onOpenSettings} />
      </ScrollView>
    );
  }

  if (result) {
    return (
      <ScrollView contentContainerStyle={styles.page}>
        <PageHeader eyebrow="Pass digitale" title="Prenotazione confermata." description="Conserva questo QR e mostralo all’ingresso." />
        <Card style={styles.ticket}>
          <View style={styles.ticketStatus}><TicketCheck color={colors.success} size={20} /><Text style={styles.ticketStatusText}>PASS ATTIVO</Text></View>
          <Text style={styles.ticketName}>{result.name} {result.surname}</Text>
          <Text style={styles.muted}>{result.eventName}</Text>
          <Image source={{ uri: qrSource(result.qrCodeBase64) }} style={styles.qr} resizeMode="contain" />
          <Text selectable style={styles.uuid}>{result.uuid}</Text>
          <Text style={styles.muted}>Conserva questo QR. Se non ricevi l’email con il codice a {result.email}, scrivi a <Text selectable style={styles.privacyLink} onPress={() => void Linking.openURL('mailto:booking@passhalo.it')}>booking@passhalo.it</Text>. Puoi comunque usare il QR mostrato qui.</Text>
          <Button label="Crea un’altra prenotazione" onPress={reset} variant="secondary" />
        </Card>
      </ScrollView>
    );
  }

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView
        contentContainerStyle={styles.page}
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={loading} onRefresh={loadEvents} tintColor={colors.accent} />}
      >
        <PageHeader eyebrow="Ingresso con QR" title="Scegli l’evento. Al resto pensiamo noi." description="Nessun account richiesto: compila i dati e ricevi subito il pass personale." />
        {error ? <Notice tone="error">{error}</Notice> : null}
        {loading && events.length === 0 ? <LoadingBlock label="Caricamento eventi…" /> : null}
        {!loading && events.length === 0 ? <Notice>Non ci sono eventi disponibili in questo momento.</Notice> : null}

        {events.length > 0 ? (
          <>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.eventRow}>
              {events.map((event) => {
                const selected = event.id === selectedId;
                return (
                  <Pressable key={event.id} onPress={() => setSelectedId(event.id)} style={[styles.eventCard, selected && styles.eventCardSelected]}>
                    <ImageBackground source={{ uri: event.imageUrl }} style={styles.eventImage} imageStyle={styles.eventImageRadius}>
                      <View style={styles.eventShade} />
                      <View style={styles.eventContent}>
                        <Text numberOfLines={2} style={styles.eventName}>{event.name}</Text>
                        <Text numberOfLines={1} style={styles.eventMeta}>{event.location}</Text>
                        <Text style={styles.eventPrice}>{money(event.bookingPrice)}</Text>
                      </View>
                    </ImageBackground>
                  </Pressable>
                );
              })}
            </ScrollView>

            {selectedEvent ? (
              <Card>
                <Text style={styles.sectionTitle}>{selectedEvent.name}</Text>
                <View style={styles.detailLine}><CalendarDays color={colors.accent} size={18} /><Text style={styles.detailText}>{date(selectedEvent.startDateTime)}</Text></View>
                <View style={styles.detailLine}><MapPin color={colors.accent} size={18} /><Text style={styles.detailText}>{selectedEvent.location}</Text></View>
                <Text style={styles.muted}>{selectedEvent.description}</Text>
                <View style={styles.priceRow}>
                  <View><Text style={styles.priceLabel}>Con prenotazione</Text><Text style={styles.priceValue}>{money(selectedEvent.bookingPrice)}</Text></View>
                  {selectedEvent.bookingPrice < selectedEvent.normalPrice ? (
                    <View><Text style={styles.priceLabel}>In cassa</Text><Text style={styles.oldPrice}>{money(selectedEvent.normalPrice)}</Text></View>
                  ) : null}
                </View>
              </Card>
            ) : null}

            <Card>
              <Text style={styles.sectionTitle}>I dati del partecipante</Text>
              <Field label="Nome" value={name} onChangeText={setName} autoCapitalize="words" autoComplete="name-given" />
              <Field label="Cognome" value={surname} onChangeText={setSurname} autoCapitalize="words" autoComplete="name-family" />
              <Field label="Email" value={email} onChangeText={setEmail} autoCapitalize="none" autoCorrect={false} keyboardType="email-address" autoComplete="email" />
              <Field label="Telefono (facoltativo)" value={phone} onChangeText={setPhone} keyboardType="phone-pad" autoComplete="tel" />
              <Pressable onPress={() => {
                const privacyUrl = `${apiBaseUrl.replace(/\/api\/?$/, '')}/privacy?eventId=${selectedEvent?.id ?? ''}`;
                void Linking.openURL(privacyUrl).catch(() => setError('Informativa privacy non disponibile.'));
              }}>
                <Text style={styles.privacyLink}>Leggi l’informativa privacy</Text>
              </Pressable>
              <View style={styles.consentRow}>
                <Text style={styles.label}>Ho letto l’informativa privacy</Text>
                <Switch value={privacyAcknowledged} onValueChange={setPrivacyAcknowledged} trackColor={{ true: colors.accent }} thumbColor={privacyAcknowledged ? colors.accentDark : colors.muted} />
              </View>
              <View style={styles.consentRow}>
                <View style={styles.consentCopy}>
                  <Text style={styles.label}>Email sui prossimi eventi di {selectedEvent?.organizerName ?? 'questo organizzatore'}</Text>
                  <Text style={styles.muted}>Facoltativo e separato dalla prenotazione.</Text>
                </View>
                <Switch value={marketingConsent} onValueChange={setMarketingConsent} trackColor={{ true: colors.accent }} thumbColor={marketingConsent ? colors.accentDark : colors.muted} />
              </View>
              <Button label="Conferma e genera il QR" onPress={submit} busy={submitting} />
            </Card>
          </>
        ) : null}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  page: { padding: spacing.lg, paddingBottom: 120, gap: spacing.lg },
  eventRow: { gap: spacing.md, paddingRight: spacing.lg },
  eventCard: { width: 255, height: 180, borderRadius: radii.large, borderWidth: 1, borderColor: colors.border, overflow: 'hidden' },
  eventCardSelected: { borderColor: colors.accent, borderWidth: 2 },
  eventImage: { flex: 1, justifyContent: 'flex-end' },
  eventImageRadius: { borderRadius: radii.large },
  eventShade: { position: 'absolute', inset: 0, backgroundColor: 'rgba(3, 5, 9, 0.54)' },
  eventContent: { padding: spacing.md, gap: 4 },
  eventName: { color: colors.white, fontSize: 21, lineHeight: 24, fontWeight: '800' },
  eventMeta: { color: '#d9deea', fontSize: 13 },
  eventPrice: { color: colors.accent, fontSize: 18, fontWeight: '900', marginTop: 4 },
  sectionTitle: { color: colors.text, fontSize: 21, fontWeight: '800' },
  muted: { color: colors.muted, fontSize: 14, lineHeight: 21 },
  detailLine: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  detailText: { color: colors.text, flex: 1, fontSize: 15 },
  priceRow: { flexDirection: 'row', gap: spacing.xl, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: spacing.md },
  priceLabel: { color: colors.muted, fontSize: 12, textTransform: 'uppercase', fontWeight: '700' },
  priceValue: { color: colors.accent, fontSize: 23, fontWeight: '900' },
  oldPrice: { color: colors.muted, fontSize: 19, fontWeight: '700', textDecorationLine: 'line-through' },
  consentRow: { flexDirection: 'row', alignItems: 'center', gap: spacing.md },
  consentCopy: { flex: 1, gap: 3 },
  label: { color: colors.text, fontSize: 14, fontWeight: '700' },
  privacyLink: { color: colors.accent, fontSize: 14, fontWeight: '700', textDecorationLine: 'underline' },
  ticket: { alignItems: 'center' },
  ticketStatus: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, alignSelf: 'stretch' },
  ticketStatusText: { color: colors.success, fontWeight: '900', letterSpacing: 1 },
  ticketName: { color: colors.text, fontSize: 28, fontWeight: '900', textAlign: 'center' },
  qr: { width: 260, height: 260, backgroundColor: colors.white, borderRadius: radii.medium },
  uuid: { color: colors.muted, fontSize: 11, textAlign: 'center' },
});
