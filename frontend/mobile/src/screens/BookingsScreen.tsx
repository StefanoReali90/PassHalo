import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, FlatList, RefreshControl, StyleSheet, Text, View } from 'react-native';
import { api } from '../api';
import { Button, Card, Field, LoadingBlock, Notice, PageHeader } from '../components/ui';
import { colors, radii, spacing } from '../theme';
import type { BookingResponse } from '../types';

const statusLabels = {
  CREATED: 'Prenotato',
  VALIDATED: 'Convalidato',
  CANCELLED: 'Annullato',
};

export function BookingsScreen() {
  const [bookings, setBookings] = useState<BookingResponse[]>([]);
  const [ownerEventIds, setOwnerEventIds] = useState<number[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [cancellingUuid, setCancellingUuid] = useState<string | null>(null);
  const [resendingUuid, setResendingUuid] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await api.bookings();
      setBookings(result.bookings);
      setOwnerEventIds(result.ownerEventIds);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Prenotazioni non disponibili.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const visibleBookings = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase('it-IT');
    if (!normalized) return bookings;
    return bookings.filter((booking) => `${booking.name} ${booking.surname} ${booking.email} ${booking.eventName} ${booking.uuid}`.toLocaleLowerCase('it-IT').includes(normalized));
  }, [bookings, query]);

  const askCancellation = (booking: BookingResponse) => {
    Alert.alert(
      'Annullare la prenotazione?',
      `${booking.name} ${booking.surname} non potrà più usare il pass per “${booking.eventName}”.`,
      [
        { text: 'Indietro', style: 'cancel' },
        {
          text: 'Annulla prenotazione',
          style: 'destructive',
          onPress: async () => {
            setCancellingUuid(booking.uuid);
            setError('');
            setMessage('');
            try {
              await api.cancelBooking(booking.uuid);
              setBookings((current) => current.map((item) => item.uuid === booking.uuid ? { ...item, bookingStatus: 'CANCELLED' } : item));
              setMessage(`Prenotazione di ${booking.name} ${booking.surname} annullata.`);
            } catch (requestError) {
              setError(requestError instanceof Error ? requestError.message : 'Annullamento non riuscito.');
            } finally {
              setCancellingUuid(null);
            }
          },
        },
      ],
    );
  };

  const resendQr = async (booking: BookingResponse) => {
    setResendingUuid(booking.uuid);
    setError('');
    setMessage('');
    try {
      await api.resendBookingQr(booking.eventId, booking.uuid);
      setMessage(`Conferma QR per ${booking.name} ${booking.surname} in coda per l’invio a ${booking.email}.`);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Reinvio del QR non riuscito.');
    } finally {
      setResendingUuid(null);
    }
  };

  return (
    <FlatList
      data={visibleBookings}
      keyExtractor={(item) => item.uuid}
      contentContainerStyle={styles.page}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={load} tintColor={colors.accent} />}
      ListHeaderComponent={(
        <View style={styles.header}>
          <PageHeader eyebrow="I tuoi eventi" title="Prenotazioni." description="Il backend restituisce soltanto le prenotazioni appartenenti agli eventi dell’admin autenticato." />
          {error ? <Notice tone="error">{error}</Notice> : null}
          {message ? <Notice tone="success">{message}</Notice> : null}
          <Field label="Cerca" value={query} onChangeText={setQuery} placeholder="Nome, email, evento o UUID" autoCapitalize="none" />
          <Text style={styles.count}>{visibleBookings.length} prenotazioni</Text>
          {loading && bookings.length === 0 ? <LoadingBlock label="Caricamento prenotazioni…" /> : null}
        </View>
      )}
      ListEmptyComponent={!loading ? <Notice>Nessuna prenotazione trovata.</Notice> : null}
      renderItem={({ item }) => (
        <Card style={styles.bookingCard}>
          <View style={styles.row}>
            <View style={styles.person}>
              <Text style={styles.name}>{item.name} {item.surname}</Text>
              <Text style={styles.muted}>{item.email}</Text>
            </View>
            <View style={[styles.badge, item.bookingStatus === 'CANCELLED' && styles.badgeCancelled, item.bookingStatus === 'VALIDATED' && styles.badgeValidated]}>
              <Text style={styles.badgeText}>{statusLabels[item.bookingStatus]}</Text>
            </View>
          </View>
          <Text style={styles.event}>{item.eventName}</Text>
          <Text style={styles.date}>{new Date(item.createdAt).toLocaleString('it-IT')}</Text>
          <Text selectable numberOfLines={1} style={styles.uuid}>{item.uuid}</Text>
          {item.bookingStatus === 'CREATED' && ownerEventIds.includes(item.eventId) ? (
            <Button label="Reinvia codice QR" variant="secondary" busy={resendingUuid === item.uuid} disabled={resendingUuid !== null} onPress={() => void resendQr(item)} />
          ) : null}
          {item.bookingStatus !== 'CANCELLED' ? (
            <Button label="Annulla prenotazione" variant="danger" busy={cancellingUuid === item.uuid} disabled={cancellingUuid !== null} onPress={() => askCancellation(item)} />
          ) : null}
        </Card>
      )}
      ItemSeparatorComponent={() => <View style={styles.separator} />}
    />
  );
}

const styles = StyleSheet.create({
  page: { padding: spacing.lg, paddingBottom: 120 },
  header: { gap: spacing.lg, marginBottom: spacing.lg },
  count: { color: colors.muted, fontSize: 13, fontWeight: '700' },
  separator: { height: spacing.md },
  bookingCard: { gap: spacing.sm },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: spacing.sm },
  person: { flex: 1 },
  name: { color: colors.text, fontSize: 18, fontWeight: '800' },
  muted: { color: colors.muted, fontSize: 13, marginTop: 3 },
  event: { color: colors.accent, fontSize: 15, fontWeight: '800' },
  date: { color: colors.muted, fontSize: 12 },
  uuid: { color: colors.muted, backgroundColor: colors.background, padding: spacing.sm, borderRadius: radii.small, fontSize: 10 },
  badge: { backgroundColor: '#313817', borderRadius: radii.pill, paddingHorizontal: 10, paddingVertical: 6 },
  badgeCancelled: { backgroundColor: colors.dangerSurface },
  badgeValidated: { backgroundColor: colors.successSurface },
  badgeText: { color: colors.text, fontSize: 10, fontWeight: '800' },
});
