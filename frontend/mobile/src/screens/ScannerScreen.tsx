import * as Haptics from 'expo-haptics';
import { CameraView, useCameraPermissions, type BarcodeScanningResult } from 'expo-camera';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { CheckCircle2, QrCode, XCircle } from 'lucide-react-native';
import { api } from '../api';
import { Button, Card, Field, Notice, PageHeader } from '../components/ui';
import { PaymentMethodSelector, paymentMethodLabel } from '../components/PaymentMethodSelector';
import { colors, radii, spacing } from '../theme';
import { isEventBookable } from '../eventAvailability';
import type { MyEvent, PaymentMethod } from '../types';

const uuidPattern = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;

function extractUuid(value: string) {
  return value.match(uuidPattern)?.[0] ?? value.trim();
}

interface HistoryItem {
  id: string;
  ok: boolean;
  message: string;
  time: string;
}

export function ScannerScreen() {
  const [permission, requestPermission] = useCameraPermissions();
  const [manualCode, setManualCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [locked, setLocked] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null);
  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [events, setEvents] = useState<MyEvent[]>([]);
  const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
  const [eventsError, setEventsError] = useState('');
  const [eventsLoading, setEventsLoading] = useState(true);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null);
  const [walkInMethod, setWalkInMethod] = useState<PaymentMethod | null>(null);
  const [walkInBusy, setWalkInBusy] = useState(false);
  const [walkInMessage, setWalkInMessage] = useState('');
  const [sessionWalkIns, setSessionWalkIns] = useState<Record<string, number>>({});
  const sessionKey = `${selectedEventId}:${walkInMethod}`;

  const loadEvents = useCallback(async () => {
    setEventsLoading(true);
    setEventsError('');
    try {
      const operational = (await api.myEvents()).filter(isEventBookable);
      setEvents(operational);
      setSelectedEventId((current) => operational.some((event) => event.id === current)
        ? current : operational[0]?.id ?? null);
    } catch (error) {
      setEventsError(error instanceof Error ? error.message : 'Eventi assegnati non disponibili.');
    } finally {
      setEventsLoading(false);
    }
  }, []);

  useEffect(() => { void loadEvents(); }, [loadEvents]);

  const validate = useCallback(async (rawValue: string) => {
    const uuid = extractUuid(rawValue);
    if (!uuid || busy || walkInBusy || locked || selectedEventId === null || paymentMethod === null) return;
    setBusy(true);
    setLocked(true);
    setResult(null);
    try {
      const checked = await api.checkIn(uuid, selectedEventId, paymentMethod);
      const message = `Pass convalidato · ${checked.eventName} · ${paymentMethodLabel(paymentMethod)}`;
      setPaymentMethod(null);
      setResult({ ok: true, message });
      setManualCode('');
      setHistory((current) => [{ id: `${Date.now()}`, ok: true, message, time: new Date().toLocaleTimeString('it-IT') }, ...current].slice(0, 6));
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch (requestError) {
      const message = requestError instanceof Error ? requestError.message : 'Pass non valido o già utilizzato.';
      setResult({ ok: false, message });
      setHistory((current) => [{ id: `${Date.now()}`, ok: false, message, time: new Date().toLocaleTimeString('it-IT') }, ...current].slice(0, 6));
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
    } finally {
      setBusy(false);
    }
  }, [busy, walkInBusy, locked, selectedEventId, paymentMethod]);

  const adjustWalkIn = async (increment: boolean) => {
    if (selectedEventId === null || walkInMethod === null || busy || walkInBusy) return;
    if (!increment && (sessionWalkIns[sessionKey] ?? 0) <= 0) return;
    setWalkInBusy(true);
    setWalkInMessage('');
    setEventsError('');
    try {
      if (increment) await api.incrementWalkIn(selectedEventId, walkInMethod);
      else await api.decrementWalkIn(selectedEventId, walkInMethod);
      setSessionWalkIns((current) => ({ ...current, [sessionKey]: (current[sessionKey] ?? 0) + (increment ? 1 : -1) }));
      setWalkInMessage(`${increment ? 'Ingresso registrato' : 'Ingresso rimosso'} · ${paymentMethodLabel(walkInMethod)}.`);
      setWalkInMethod(null);
    } catch (error) {
      setEventsError(error instanceof Error ? error.message : 'Conteggio non aggiornato.');
    } finally {
      setWalkInBusy(false);
    }
  };

  const onBarcodeScanned = ({ data }: BarcodeScanningResult) => {
    void validate(data);
  };

  const unlock = () => {
    setResult(null);
    setLocked(false);
  };

  return (
    <ScrollView contentContainerStyle={styles.page} keyboardShouldPersistTaps="handled">
      <PageHeader eyebrow="Controllo ingressi" title="Un pass. Un ingresso." description="La convalida avviene subito sul server e impedisce il riutilizzo dello stesso QR." />

      {eventsError ? <Notice tone="error">{eventsError}</Notice> : null}
      {events.length === 0 && !eventsError && !eventsLoading ? <Notice>Nessun evento attivo assegnato.</Notice> : null}
      <Button label="Aggiorna eventi" onPress={() => void loadEvents()} variant="secondary" busy={eventsLoading} />
      {events.length > 0 ? (
        <Card>
          <Text style={styles.cameraTitle}>Evento da controllare</Text>
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.eventRow}>
            {events.map((event) => (
              <Pressable key={event.id} disabled={busy || walkInBusy} onPress={() => { setSelectedEventId(event.id); setPaymentMethod(null); setWalkInMethod(null); setWalkInMessage(''); }}
                accessibilityRole="button" accessibilityState={{ selected: selectedEventId === event.id }}
                style={[styles.eventChoice, selectedEventId === event.id && styles.eventChoiceSelected]}>
                <Text style={styles.eventChoiceText}>{event.name}</Text>
              </Pressable>
            ))}
          </ScrollView>
        </Card>
      ) : null}

      {result ? (
        <View style={[styles.result, result.ok ? styles.resultOk : styles.resultKo]}>
          {result.ok ? <CheckCircle2 color={colors.success} size={42} /> : <XCircle color={colors.danger} size={42} />}
          <Text style={[styles.resultTitle, { color: result.ok ? colors.success : colors.danger }]}>{result.ok ? 'INGRESSO OK' : 'INGRESSO KO'}</Text>
          <Text style={styles.resultMessage}>{result.message}</Text>
          <Button label="Scansiona un altro pass" onPress={unlock} variant="secondary" />
        </View>
      ) : null}

      <Card style={styles.cameraCard}>
        <PaymentMethodSelector value={paymentMethod} onChange={setPaymentMethod} disabled={busy || walkInBusy} label="Pagamento del pass da convalidare" />
        {!permission ? (
          <View style={styles.cameraPlaceholder}><Text style={styles.muted}>Verifica permesso fotocamera…</Text></View>
        ) : !permission.granted ? (
          <View style={styles.cameraPlaceholder}>
            <QrCode color={colors.accent} size={46} />
            <Text style={styles.cameraTitle}>Serve la fotocamera</Text>
            <Text style={styles.muted}>Consenti l’accesso per leggere i QR dei partecipanti.</Text>
            <Button label="Consenti fotocamera" onPress={() => void requestPermission()} />
          </View>
        ) : (
          <View style={styles.cameraFrame}>
            <CameraView
              style={StyleSheet.absoluteFill}
              facing="back"
              barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
              onBarcodeScanned={busy || walkInBusy || locked || selectedEventId === null || paymentMethod === null ? undefined : onBarcodeScanned}
            />
            <View pointerEvents="none" style={styles.target} />
          </View>
        )}

        <Text style={styles.or}>oppure inserisci il codice</Text>
        <Field label="UUID del pass" value={manualCode} onChangeText={setManualCode} autoCapitalize="none" autoCorrect={false} />
        <Button label="Convalida" onPress={() => void validate(manualCode)} busy={busy} disabled={walkInBusy || !manualCode.trim() || locked || selectedEventId === null || paymentMethod === null} />
      </Card>

      <Card>
        <Text style={styles.cameraTitle}>Ingressi senza prenotazione</Text>
        <Text style={styles.muted}>Registrati da questo dispositivo: {sessionWalkIns[`${selectedEventId}:CASH`] ?? 0} contanti · {sessionWalkIns[`${selectedEventId}:CARD`] ?? 0} carta</Text>
        <PaymentMethodSelector value={walkInMethod} onChange={setWalkInMethod} disabled={busy || walkInBusy} />
        <Button label="Aggiungi ingresso" onPress={() => void adjustWalkIn(true)} busy={walkInBusy} disabled={busy || selectedEventId === null || walkInMethod === null} />
        <Button label="Correggi metodo selezionato" variant="secondary" onPress={() => void adjustWalkIn(false)} disabled={busy || walkInBusy || selectedEventId === null || walkInMethod === null || (sessionWalkIns[sessionKey] ?? 0) <= 0} />
        {walkInMessage ? <Notice tone="success">{walkInMessage}</Notice> : null}
      </Card>

      <Card>
        <Text style={styles.cameraTitle}>Ultimi controlli</Text>
        {history.length === 0 ? <Text style={styles.muted}>Nessun pass controllato in questa sessione.</Text> : history.map((item) => (
          <View key={item.id} style={styles.historyRow}>
            {item.ok ? <CheckCircle2 color={colors.success} size={19} /> : <XCircle color={colors.danger} size={19} />}
            <View style={styles.historyCopy}>
              <Text numberOfLines={2} style={styles.historyMessage}>{item.message}</Text>
              <Text style={styles.historyTime}>{item.time}</Text>
            </View>
          </View>
        ))}
      </Card>
      <Notice>Il check-in richiede una connessione attiva: non viene accettato in modalità offline.</Notice>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  page: { padding: spacing.lg, paddingBottom: 120, gap: spacing.lg },
  cameraCard: { padding: spacing.md },
  eventRow: { gap: spacing.sm, paddingTop: spacing.md },
  eventChoice: { borderWidth: 1, borderColor: colors.border, borderRadius: radii.medium, padding: spacing.md },
  eventChoiceSelected: { borderColor: colors.accent, backgroundColor: colors.surface },
  eventChoiceText: { color: colors.text, fontWeight: '700' },
  cameraFrame: { height: 390, borderRadius: radii.large, overflow: 'hidden', backgroundColor: '#000' },
  cameraPlaceholder: { minHeight: 280, alignItems: 'center', justifyContent: 'center', gap: spacing.md, padding: spacing.lg },
  target: { position: 'absolute', width: 220, height: 220, borderColor: colors.accent, borderWidth: 3, borderRadius: 24, alignSelf: 'center', top: 85 },
  cameraTitle: { color: colors.text, fontSize: 20, fontWeight: '800', textAlign: 'center' },
  muted: { color: colors.muted, fontSize: 14, lineHeight: 21, textAlign: 'center' },
  or: { color: colors.muted, textAlign: 'center', fontSize: 12, textTransform: 'uppercase', letterSpacing: 1 },
  result: { borderRadius: radii.large, borderWidth: 1, padding: spacing.lg, alignItems: 'center', gap: spacing.sm },
  resultOk: { backgroundColor: colors.successSurface, borderColor: colors.success },
  resultKo: { backgroundColor: colors.dangerSurface, borderColor: colors.danger },
  resultTitle: { fontSize: 25, fontWeight: '900', letterSpacing: 1 },
  resultMessage: { color: colors.text, textAlign: 'center', fontSize: 16, lineHeight: 23 },
  historyRow: { flexDirection: 'row', gap: spacing.sm, alignItems: 'flex-start', borderTopWidth: 1, borderTopColor: colors.border, paddingTop: spacing.sm },
  historyCopy: { flex: 1 },
  historyMessage: { color: colors.text, fontSize: 14 },
  historyTime: { color: colors.muted, fontSize: 11, marginTop: 3 },
});
