import { StyleSheet, Text, View } from 'react-native';
import type { EventDashboardResponse } from '../types';
import { colors, spacing } from '../theme';
import { Card } from './ui';

export function PaymentCounts({ data }: { data: EventDashboardResponse }) {
  const rows = [
    { label: 'Contanti', booked: data.checkedInCashCount, walkIn: data.walkInCashCount },
    { label: 'Carta / POS', booked: data.checkedInCardCount, walkIn: data.walkInCardCount },
  ];
  if (data.checkedInUnrecordedCount + data.walkInUnrecordedCount > 0) {
    rows.push({ label: 'Metodo non registrato', booked: data.checkedInUnrecordedCount, walkIn: data.walkInUnrecordedCount });
  }
  return (
    <Card>
      <Text style={styles.title}>Persone per pagamento</Text>
      {rows.map((row) => (
        <View key={row.label} style={styles.row}>
          <View style={styles.copy}>
            <Text style={styles.label}>{row.label}</Text>
            <Text style={styles.note}>{row.booked.toLocaleString('it-IT')} prenotati · {row.walkIn.toLocaleString('it-IT')} senza prenotazione</Text>
          </View>
          <Text style={styles.count}>{(row.booked + row.walkIn).toLocaleString('it-IT')}</Text>
        </View>
      ))}
      <Text style={styles.note}>Totale entrati: {data.totalAttendees.toLocaleString('it-IT')}</Text>
    </Card>
  );
}

const styles = StyleSheet.create({
  title: { color: colors.text, fontSize: 20, fontWeight: '800' },
  row: { flexDirection: 'row', alignItems: 'center', gap: spacing.sm, borderTopWidth: 1, borderTopColor: colors.border, paddingTop: spacing.sm },
  copy: { flex: 1, gap: 4 },
  label: { color: colors.text, fontSize: 16, fontWeight: '700' },
  note: { color: colors.muted, fontSize: 12, lineHeight: 18 },
  count: { color: colors.accent, fontSize: 28, fontWeight: '900' },
});
