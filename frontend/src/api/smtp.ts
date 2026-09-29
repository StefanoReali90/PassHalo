import { apiFetch } from './client';

export interface SmtpSettings {
    configured: boolean;
    host: string | null;
    port: number | null;
    encryption: 'STARTTLS' | 'SSL' | null;
    username: string | null;
    fromEmail: string | null;
    fromName: string | null;
}

export interface SmtpSettingsInput {
    host: string;
    port: number;
    encryption: 'STARTTLS' | 'SSL';
    username: string;
    password: string;
    fromEmail: string;
    fromName: string;
}

export const getSmtpSettings = () => apiFetch<SmtpSettings>('/account/smtp');
export const saveSmtpSettings = (settings: SmtpSettingsInput) => apiFetch<SmtpSettings>('/account/smtp', {
    method: 'PUT', body: JSON.stringify(settings),
});
export const deleteSmtpSettings = () => apiFetch<void>('/account/smtp', { method: 'DELETE' });
export const testSmtpSettings = () => apiFetch<void>('/account/smtp/test', { method: 'POST', timeoutMs: 30_000 });
