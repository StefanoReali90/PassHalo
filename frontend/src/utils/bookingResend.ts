export function bookingResendMailto(eventName?: string, email?: string): string {
    const subject = 'Richiesta reinvio codice QR PassHalo';
    const details = [
        'Buongiorno, non ho ricevuto il codice QR della mia prenotazione. Potete reinviarlo?',
        '',
        `Evento: ${eventName || '[nome evento]'}`,
        `Email usata per la prenotazione: ${email || '[la tua email]'}`,
    ];

    return `mailto:booking@passhalo.it?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(details.join('\n'))}`;
}
