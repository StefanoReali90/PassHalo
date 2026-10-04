import type { PaymentMethod } from '../types';

export const paymentMethodLabel = (method: PaymentMethod) => method === 'CASH' ? 'Contanti' : 'Carta / POS';
