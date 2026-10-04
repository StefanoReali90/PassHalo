import { apiFetch } from './client';

export async function downloadMarketingContacts(): Promise<void> {
    const csv = await apiFetch<Blob>('/marketing/contacts.csv', { responseType: 'blob', timeoutMs: 60_000 });
    const url = URL.createObjectURL(csv);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'passhalo-marketing-contacts.csv';
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
}
