import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { api } from '../api';
import { Button, Card, LoadingBlock, Notice, PageHeader } from '../components/ui';
import { colors, radii, spacing } from '../theme';
import type { EventDashboardResponse, PassHaloEvent } from '../types';

const number = (value: number) => value.toLocaleString('it-IT');
const money = (value: number) => value.toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });

export function DashboardScreen() {
  const [events, setEvents] = useState<PassHaloEvent[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [data, setData] = useState<EventDashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [actionBusy, setActionBusy] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const selectedIdRef = useRef<number | null>(null);

  const loadDashboard = useCallback(async (eventId: number) => {
    setError('');
    setData(await api.dashboard(eventId));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const managedEvents = (await api.myEvents()).filter((event) => event.role === 'EVENT_ADMIN');
      setEvents(managedEvents);
      const currentId = selectedIdRef.current;
      const nextId = managedEvents.some((event) => event.id === currentId) ? currentId : managedEvents[0]?.id ?? null;
      selectedIdRef.current = nextId;
      setSelectedId(nextId);
      if (nextId !== null) await loadDashboard(nextId);
      else setData(null);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Dashboard non disponibile.');
    } finally {
      setLoading(false);
    }
  }, [loadDashboard]);

  useEffect(() => {
    void load();
  }, [load]);

  const selectEvent = async (eventId: number) => {
    selectedIdRef.current = eventId;
    setSelectedId(eventId);
    setLoading(true);
    setMessage('');
    try {
      await loadDashboard(eventId);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Statistiche non disponibili.');
    } finally {
      setLoading(false);
    }
  };

  const adjustWalkIn = async (direction: 'plus' | 'minus') => {
    if (selectedId === null) return;
    setActionBusy(true);
    setError('');
    setMessage('');
    try {
      if (direction === 'plus') await api.incrementWalkIn(selectedId);
      else await api.decrementWalkIn(selectedId);
      await loadDashboard(selectedId);
      setMessage(direction === 'plus' ? 'Ingresso in cassa aggiunto.' : 'Ingresso in cassa rimosso.');
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Aggiornamento non riuscito.');
    } finally {
      setActionBusy(false);
    }
  };

  const closeSelectedEvent = () => {
    const event = events.find((item) => item.id === selectedId);
    if (!event) return;
    Alert.alert(
      'Chiudere l’evento?',
      `I dati personali di “${event.name}” saranno anonimizzati. L’operazione è definitiva.`,
      [
        { text: 'Annulla', style: 'cancel' },
        {
          text: 'Chiudi evento',
          style: 'destructive',
          onPress: async () => {
            setActionBusy(true);
            setError('');
            try {
              await api.closeEvent(event.id);
              setEvents((current) => current.map((item) => item.id === event.id ? { ...item, eventState: 'FINISHED' } : item));
              await loadDashboard(event.id);
              setMessage('Evento chiuso e dati personali anonimizzati.');
            } catch (requestError) {
              setError(requestError instanceof Error ? requestError.message : 'Chiusura non riuscita.');
            } finally {
              setActionBusy(false);
            }
          },
        },
      ],
    );
  };

  const selectedEvent = events.find((event) => event.id === selectedId);
  const finished = selectedEvent?.eventState === 'FINISHED';

  return (
    <ScrollView
      contentContainerStyle={styles.page}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={load} tintColor={colors.accent} />}
    >
      <PageHeader eyebrow="I tuoi eventi" title="Risultati in tempo reale." description="Statistiche e operazioni mostrano esclusivamente gli eventi dell’admin autenticato." />
      {error ? <Notice tone="error">{error}</Notice> : null}
      {message ? <Notice tone="success">{message}</Notice> : null}

      {events.length > 0 ? (
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips}>
          {events.map((event) => (
            <Pressable key={event.id} onPress={() => void selectEvent(event.id)} style={[styles.chip, event.id === selectedId && styles.chipActive]}>
              <Text style={[styles.chipText, event.id === selectedId && styles.chipTextActive]}>{event.name}</Text>
            </Pressable>
          ))}
        </ScrollView>
      ) : null}

      {loading && !data ? <LoadingBlock label="Caricamento statistiche…" /> : null}
      {!loading && events.length === 0 ? <Notice>Non hai ancora creato eventi. Puoi crearli dalla web app amministrativa.</Notice> : null}

      {data ? (
        <>
          <View style={styles.metrics}>
            <Metric label="Prenotazioni" value={number(data.totalBookings)} note={`${number(data.noShowCount)} non presentati`} />
            <Metric label="Ingressi" value={number(data.totalAttendees)} note={`${number(data.checkedInCount)} QR + ${number(data.walkInCount)} cassa`} />
            <Metric label="Partecipazione" value={`${data.attendanceRate.toFixed(1).replace('.0', '')}%`} note={`${number(data.checkedInCount)} pass convalidati`} />
            <Metric label="Ricavo" value={money(data.totalRevenue)} note="Ingressi registrati" />
          </View>

          <Card>
            <Text style={styles.cardTitle}>Ingressi senza prenotazione</Text>
            <Text style={styles.walkInCount}>{number(data.walkInCount)}</Text>
            <Text style={styles.muted}>Registra chi paga direttamente in cassa.</Text>
            <View style={styles.actions}>
              <View style={styles.action}><Button compact label="− Rimuovi" variant="secondary" disabled={finished || data.walkInCount <= 0} busy={actionBusy} onPress={() => void adjustWalkIn('minus')} /></View>
              <View style={styles.action}><Button compact label="+ Aggiungi" disabled={finished} busy={actionBusy} onPress={() => void adjustWalkIn('plus')} /></View>
            </View>
          </Card>

          <Card>
            <Text style={styles.cardTitle}>Fine evento</Text>
            <Text style={styles.muted}>La chiusura anonimizza i dati personali e conserva le statistiche aggregate.</Text>
            <Button label={finished ? 'Evento già chiuso' : 'Chiudi evento'} variant="danger" disabled={finished} busy={actionBusy} onPress={closeSelectedEvent} />
          </Card>
        </>
      ) : null}
    </ScrollView>
  );
}

function Metric({ label, value, note }: { label: string; value: string; note: string }) {
  return (
    <View style={styles.metric}>
      <Text style={styles.metricLabel}>{label}</Text>
      <Text numberOfLines={1} adjustsFontSizeToFit style={styles.metricValue}>{value}</Text>
      <Text style={styles.metricNote}>{note}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  page: { padding: spacing.lg, paddingBottom: 120, gap: spacing.lg },
  chips: { gap: spacing.sm, paddingRight: spacing.lg },
  chip: { backgroundColor: colors.surface, borderWidth: 1, borderColor: colors.border, borderRadius: radii.pill, paddingHorizontal: 17, paddingVertical: 11 },
  chipActive: { backgroundColor: colors.accent, borderColor: colors.accent },
  chipText: { color: colors.text, fontWeight: '700' },
  chipTextActive: { color: colors.accentDark },
  metrics: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing.sm },
  metric: { width: '48%', minHeight: 142, backgroundColor: colors.surface, borderColor: colors.border, borderWidth: 1, borderRadius: radii.large, padding: spacing.md, justifyContent: 'space-between' },
  metricLabel: { color: colors.muted, fontSize: 12, fontWeight: '700', textTransform: 'uppercase' },
  metricValue: { color: colors.text, fontSize: 29, fontWeight: '900' },
  metricNote: { color: colors.muted, fontSize: 11, lineHeight: 16 },
  cardTitle: { color: colors.text, fontSize: 20, fontWeight: '800' },
  walkInCount: { color: colors.accent, fontSize: 48, fontWeight: '900' },
  muted: { color: colors.muted, fontSize: 14, lineHeight: 21 },
  actions: { flexDirection: 'row', gap: spacing.sm },
  action: { flex: 1 },
});
