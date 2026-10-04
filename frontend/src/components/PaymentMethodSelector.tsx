import { Banknote, CreditCard } from 'lucide-react';
import type { PaymentMethod } from '../types';

export function PaymentMethodSelector({ value, onChange, disabled = false, label = 'Metodo di pagamento' }: {
    value: PaymentMethod | null;
    onChange: (method: PaymentMethod) => void;
    disabled?: boolean;
    label?: string;
}) {
    return (
        <fieldset className="payment-selector" disabled={disabled}>
            <legend>{label}</legend>
            <div className="payment-choices">
                <button type="button" className={`button ${value === 'CASH' ? 'primary' : ''}`} aria-pressed={value === 'CASH'} onClick={() => onChange('CASH')}><Banknote size={18} /> Contanti</button>
                <button type="button" className={`button ${value === 'CARD' ? 'primary' : ''}`} aria-pressed={value === 'CARD'} onClick={() => onChange('CARD')}><CreditCard size={18} /> Carta / POS</button>
            </div>
            {value === null && <p className="payment-hint">Seleziona come ha pagato questa persona.</p>}
        </fieldset>
    );
}
