import { apiFetch } from './client';

export interface BrevoStatus {
    connected: boolean;
    listId: number | null;
    pendingContacts: number;
}

export function getBrevoStatus(): Promise<BrevoStatus> {
    return apiFetch<BrevoStatus>('/marketing/brevo');
}

export function connectBrevo(apiKey: string, listId: number): Promise<BrevoStatus> {
    return apiFetch<BrevoStatus>('/marketing/brevo', {
        method: 'POST',
        body: JSON.stringify({ apiKey, listId }),
        timeoutMs: 25_000,
    });
}

export function rotateBrevoKey(apiKey: string): Promise<BrevoStatus> {
    return apiFetch<BrevoStatus>('/marketing/brevo', {
        method: 'PUT',
        body: JSON.stringify({ apiKey }),
        timeoutMs: 25_000,
    });
}

export function disconnectBrevo(): Promise<void> {
    return apiFetch<void>('/marketing/brevo', { method: 'DELETE', timeoutMs: 60_000 });
}
