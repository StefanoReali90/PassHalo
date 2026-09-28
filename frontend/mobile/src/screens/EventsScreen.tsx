import DateTimePicker, { type DateTimePickerEvent } from '@react-native-community/datetimepicker';
import { useCallback, useEffect, useState } from 'react';
import { Alert, Image, Pressable, RefreshControl, ScrollView, StyleSheet, Text, View } from 'react-native';
import { CalendarDays, Clock3, MapPin, Plus } from 'lucide-react-native';
import { api } from '../api';
import { Button, Card, Field, LoadingBlock, Notice, PageHeader } from '../components/ui';
import { colors, radii, spacing } from '../theme';
import type { EventRequest, PassHaloEvent } from '../types';

interface EventDraft {
  name: string;
  description: string;
  location: string;
  imageUrl: string;
  videoUrl: string;
  totalTickets: string;
  normalPrice: string;
  bookingPrice: string;
  start: Date;
  end: Date;
}

type DateField = 'start' | 'end';
type PickerState = { field: DateField; mode: 'date' | 'time' } | null;

function freshDraft(): EventDraft {
  const start = new Date();
  start.setDate(start.getDate() + 1);
  start.setHours(20, 0, 0, 0);
  const end = new Date(start.getTime() + 4 * 60 * 60 * 1000);
  return { name: '', description: '', location: '', imageUrl: '', videoUrl: '', totalTickets: '', normalPrice: '', bookingPrice: '', start, end };
}

function draftFromEvent(event: PassHaloEvent): EventDraft {
  return {
    name: event.name,
    description: event.description,
    location: event.location,
    imageUrl: event.imageUrl,
    videoUrl: event.videoUrl ?? '',
    totalTickets: String(event.totalTickets),
    normalPrice: String(event.normalPrice),
    bookingPrice: String(event.bookingPrice),
    start: new Date(event.startDateTime),
    end: new Date(event.endDateTime),
  };
}

function localDateTime(value: Date) {
  const part = (number: number) => String(number).padStart(2, '0');
  return `${value.getFullYear()}-${part(value.getMonth() + 1)}-${part(value.getDate())}T${part(value.getHours())}:${part(value.getMinutes())}:00`;
}

const dateLabel = (value: Date) => value.toLocaleDateString('it-IT', { dateStyle: 'medium' });
const timeLabel = (value: Date) => value.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' });
const money = (value: number) => value.toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
const stateLabels = { WAITING: 'In attesa', IN_PROGRESS: 'In corso', FINISHED: 'Concluso' };

export function EventsScreen({ canCreateEvent }: { canCreateEvent: boolean }) {
  const [events, setEvents] = useState<PassHaloEvent[]>([]);
  const [editing, setEditing] = useState<PassHaloEvent | null>(null);
  const [draft, setDraft] = useState<EventDraft>(freshDraft);
  const [formOpen, setFormOpen] = useState(false);
  const [picker, setPicker] = useState<PickerState>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setEvents((await api.myEvents()).filter((event) => event.role === 'EVENT_ADMIN'));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Eventi non disponibili.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setDraft(freshDraft());
    setError('');
    setMessage('');
    setFormOpen(true);
  };

  const openEdit = (event: PassHaloEvent) => {
    setEditing(event);
    setDraft(draftFromEvent(event));
    setError('');
    setMessage('');
    setFormOpen(true);
  };

  const update = <K extends keyof EventDraft>(field: K, value: EventDraft[K]) => {
    setDraft((current) => ({ ...current, [field]: value }));
  };

  const onPickerChange = (_event: DateTimePickerEvent, selected?: Date) => {
    if (!picker) return;
    if (selected) {
      const currentValue = new Date(draft[picker.field]);
      if (picker.mode === 'date') currentValue.setFullYear(selected.getFullYear(), selected.getMonth(), selected.getDate());
      else currentValue.setHours(selected.getHours(), selected.getMinutes(), 0, 0);
      update(picker.field, currentValue);
    }
    setPicker(null);
  };

  const save = async () => {
    const totalTickets = Number(draft.totalTickets);
    const normalPrice = Number(draft.normalPrice.replace(',', '.'));
    const bookingPrice = Number(draft.bookingPrice.replace(',', '.'));

    if (!draft.name.trim() || !draft.description.trim() || !draft.location.trim() || !draft.imageUrl.trim()) {
      setError('Compila nome, descrizione, luogo e URL dell’immagine.');
      return;
    }
    if (!Number.isInteger(totalTickets) || totalTickets <= 0 || normalPrice <= 0 || bookingPrice <= 0) {
      setError('Posti e prezzi devono essere numeri positivi.');
      return;
    }
    if (bookingPrice > normalPrice) {
      setError('Il prezzo prenotazione non può superare il prezzo normale.');
      return;
    }
    if (draft.start >= draft.end) {
      setError('La fine dell’evento deve essere successiva all’inizio.');
      return;
    }

    const payload: EventRequest = {
      name: draft.name.trim(),
      description: draft.description.trim(),
      location: draft.location.trim(),
      imageUrl: draft.imageUrl.trim(),
      videoUrl: draft.videoUrl.trim() || null,
      totalTickets,
      normalPrice,
      bookingPrice,
      start: localDateTime(draft.start),
      end: localDateTime(draft.end),
      ...(editing?.faqs ? { faqs: editing.faqs } : {}),
    };

    setSaving(true);
    setError('');
    try {
      if (editing) await api.updateEvent(editing.id, payload);
      else await api.createEvent(payload);
      setMessage(editing ? 'Evento aggiornato.' : 'Evento creato.');
      setFormOpen(false);
      setEditing(null);
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Salvataggio non riuscito.');
    } finally {
      setSaving(false);
    }
  };

  const askDelete = (event: PassHaloEvent) => {
    Alert.alert('Eliminare l’evento?', `Vuoi eliminare “${event.name}”?`, [
      { text: 'Annulla', style: 'cancel' },
      {
        text: 'Elimina',
        style: 'destructive',
        onPress: async () => {
          setError('');
          setMessage('');
          try {
            await api.deleteEvent(event.id);
            setEvents((current) => current.filter((item) => item.id !== event.id));
            setMessage('Evento eliminato.');
          } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Eliminazione non riuscita.');
          }
        },
      },
    ]);
  };

  if (formOpen) {
    return (
      <ScrollView contentContainerStyle={styles.page} keyboardShouldPersistTaps="handled">
        <PageHeader eyebrow={editing ? 'Modifica evento' : 'Nuovo evento'} title={editing ? editing.name : 'Pubblica un evento.'} description={editing ? 'Aggiorna le informazioni dell’evento.' : 'Il nuovo evento sarà associato al tuo account.'} />
        {error ? <Notice tone="error">{error}</Notice> : null}
        <Card>
          <Field label="Nome" value={draft.name} onChangeText={(value) => update('name', value)} />
          <Field label="Descrizione" value={draft.description} onChangeText={(value) => update('description', value)} multiline />
          <Field label="Luogo" value={draft.location} onChangeText={(value) => update('location', value)} />
          <Field label="URL immagine" value={draft.imageUrl} onChangeText={(value) => update('imageUrl', value)} keyboardType="url" autoCapitalize="none" />
          <Field label="URL video (facoltativo)" value={draft.videoUrl} onChangeText={(value) => update('videoUrl', value)} keyboardType="url" autoCapitalize="none" />

          <Text style={styles.sectionLabel}>Inizio</Text>
          <View style={styles.dateRow}>
            <DateButton icon="date" label={dateLabel(draft.start)} onPress={() => setPicker({ field: 'start', mode: 'date' })} />
            <DateButton icon="time" label={timeLabel(draft.start)} onPress={() => setPicker({ field: 'start', mode: 'time' })} />
          </View>
          <Text style={styles.sectionLabel}>Fine</Text>
          <View style={styles.dateRow}>
            <DateButton icon="date" label={dateLabel(draft.end)} onPress={() => setPicker({ field: 'end', mode: 'date' })} />
            <DateButton icon="time" label={timeLabel(draft.end)} onPress={() => setPicker({ field: 'end', mode: 'time' })} />
          </View>

          <Field label="Posti disponibili" value={draft.totalTickets} onChangeText={(value) => update('totalTickets', value)} keyboardType="number-pad" />
          <View style={styles.priceFields}>
            <View style={styles.priceField}><Field label="Prezzo normale" value={draft.normalPrice} onChangeText={(value) => update('normalPrice', value)} keyboardType="decimal-pad" /></View>
            <View style={styles.priceField}><Field label="Prezzo prenotato" value={draft.bookingPrice} onChangeText={(value) => update('bookingPrice', value)} keyboardType="decimal-pad" /></View>
          </View>
          <Button label={editing ? 'Salva modifiche' : 'Crea evento'} onPress={save} busy={saving} />
          <Button label="Annulla" onPress={() => setFormOpen(false)} variant="ghost" disabled={saving} />
        </Card>
        {picker ? <DateTimePicker value={draft[picker.field]} mode={picker.mode} is24Hour onChange={onPickerChange} /> : null}
      </ScrollView>
    );
  }

  return (
    <ScrollView contentContainerStyle={styles.page} refreshControl={<RefreshControl refreshing={loading} onRefresh={load} tintColor={colors.accent} />}>
      <PageHeader eyebrow="Workspace" title="I tuoi eventi." description="Eventi di tua proprietà o assegnati come amministratore." action={canCreateEvent ? <Pressable onPress={openCreate} style={styles.addButton}><Plus color={colors.accentDark} size={22} /></Pressable> : undefined} />
      {error ? <Notice tone="error">{error}</Notice> : null}
      {message ? <Notice tone="success">{message}</Notice> : null}
      {loading && events.length === 0 ? <LoadingBlock label="Caricamento eventi…" /> : null}
      {!loading && events.length === 0 ? <Notice>{canCreateEvent ? 'Non hai ancora creato eventi. Premi il pulsante + per iniziare.' : 'Non hai eventi assegnati come amministratore.'}</Notice> : null}

      {events.map((event) => (
        <Card key={event.id}>
          <Image source={{ uri: event.imageUrl }} style={styles.image} />
          <View style={styles.titleRow}>
            <View style={styles.titleCopy}>
              <Text style={styles.eventName}>{event.name}</Text>
              <Text style={styles.state}>{stateLabels[event.eventState]}</Text>
            </View>
            <Text style={styles.price}>{money(event.bookingPrice)}</Text>
          </View>
          <View style={styles.info}><CalendarDays color={colors.accent} size={17} /><Text style={styles.infoText}>{new Date(event.startDateTime).toLocaleString('it-IT')}</Text></View>
          <View style={styles.info}><MapPin color={colors.accent} size={17} /><Text style={styles.infoText}>{event.location}</Text></View>
          <Text numberOfLines={3} style={styles.description}>{event.description}</Text>
          <View style={styles.actions}>
            <View style={styles.action}><Button compact label="Modifica" variant="secondary" disabled={event.eventState === 'FINISHED'} onPress={() => openEdit(event)} /></View>
            <View style={styles.action}><Button compact label="Elimina" variant="danger" disabled={event.eventState === 'FINISHED'} onPress={() => askDelete(event)} /></View>
          </View>
        </Card>
      ))}
    </ScrollView>
  );
}

function DateButton({ icon, label, onPress }: { icon: 'date' | 'time'; label: string; onPress(): void }) {
  return (
    <Pressable onPress={onPress} style={styles.dateButton}>
      {icon === 'date' ? <CalendarDays color={colors.accent} size={18} /> : <Clock3 color={colors.accent} size={18} />}
      <Text style={styles.dateButtonText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  page: { padding: spacing.lg, paddingBottom: 120, gap: spacing.lg },
  addButton: { width: 46, height: 46, borderRadius: 23, backgroundColor: colors.accent, alignItems: 'center', justifyContent: 'center' },
  sectionLabel: { color: colors.text, fontSize: 13, fontWeight: '800' },
  dateRow: { flexDirection: 'row', gap: spacing.sm },
  dateButton: { flex: 1, minHeight: 50, backgroundColor: colors.background, borderColor: colors.border, borderWidth: 1, borderRadius: radii.medium, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing.sm },
  dateButtonText: { color: colors.text, fontWeight: '700' },
  priceFields: { flexDirection: 'row', gap: spacing.sm },
  priceField: { flex: 1 },
  image: { width: '100%', height: 170, borderRadius: radii.medium, backgroundColor: colors.background },
  titleRow: { flexDirection: 'row', gap: spacing.md, alignItems: 'flex-start' },
  titleCopy: { flex: 1 },
  eventName: { color: colors.text, fontSize: 21, fontWeight: '900' },
  state: { color: colors.muted, fontSize: 11, fontWeight: '800', marginTop: 3 },
  price: { color: colors.accent, fontSize: 20, fontWeight: '900' },
  info: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm },
  infoText: { color: colors.text, fontSize: 14, flex: 1 },
  description: { color: colors.muted, fontSize: 14, lineHeight: 21 },
  actions: { flexDirection: 'row', gap: spacing.sm },
  action: { flex: 1 },
});
