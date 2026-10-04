import { StyleSheet, Text, View } from 'react-native';
import type { PaymentMethod } from '../types';
import { colors, spacing } from '../theme';
import { Button } from './ui';

export const paymentMethodLabel = (method: PaymentMethod) => method === 'CASH' ? 'Contanti' : 'Carta / POS';

export function PaymentMethodSelector({ value, onChange, disabled = false, label = 'Metodo di pagamento' }: {
  value: PaymentMethod | null;
  onChange: (method: PaymentMethod) => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <View style={styles.selector}>
      <Text style={styles.label}>{label}</Text>
      <View style={styles.choices}>
        <View style={styles.choice}><Button compact label={`${value === 'CASH' ? '✓ ' : ''}Contanti`} variant={value === 'CASH' ? 'primary' : 'secondary'} disabled={disabled} onPress={() => onChange('CASH')} /></View>
        <View style={styles.choice}><Button compact label={`${value === 'CARD' ? '✓ ' : ''}Carta / POS`} variant={value === 'CARD' ? 'primary' : 'secondary'} disabled={disabled} onPress={() => onChange('CARD')} /></View>
      </View>
      {value === null ? <Text style={styles.hint}>Seleziona come ha pagato questa persona.</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  selector: { gap: spacing.sm },
  label: { color: colors.text, fontSize: 15, fontWeight: '700' },
  choices: { flexDirection: 'row', gap: spacing.sm },
  choice: { flex: 1 },
  hint: { color: colors.muted, fontSize: 12 },
});
