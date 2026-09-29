import { useEffect, useState, type FormEvent } from 'react';
import { Download, Filter, Mail, RefreshCw, Search, TicketX } from 'lucide-react';
import {
    cancelBooking,
    getBookingById,
    getBookingByUUID,
    getBookingsByEventAndEmail,
    getBookingsByEventId,
    resendBookingQr,
} from '../api/booking';
import { getMyEvents } from '../api/events';
import { ConfirmDialog } from '../components/ConfirmDialog';
import type { BookingResponse, MyEvent } from '../types';

type SearchMode = 'all' | 'event' | 'email' | 'event-email' | 'uuid' | 'id';

const modeLabels: Record<SearchMode, string> = {
    all: 'Tutte',
    event: 'Per evento',
    email: 'Per email',
    'event-email': 'Evento + email',
    uuid: 'Per UUID',
    id: 'Per ID interno',
};

const statusLabels = {
    CREATED: 'Prenotato',
    VALIDATED: 'Convalidato',
    CANCELLED: 'Annullato',
};

function qrSource(value: string) {
    return value.startsWith('data:') ? value : `data:image/png;base64,${value}`;
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat('it-IT', {
        dateStyle: 'short',
        timeStyle: 'short',
    }).format(new Date(value));
}

export function BookingsPage() {
    const [bookings, setBookings] = useState<BookingResponse[]>([]);
    const [events, setEvents] = useState<MyEvent[]>([]);
    const [mode, setMode] = useState<SearchMode>('all');
    const [eventId, setEventId] = useState<number | null>(null);
    const [email, setEmail] = useState('');
    const [identifier, setIdentifier] = useState('');
    const [loading, setLoading] = useState(true);
    const [cancelling, setCancelling] = useState(false);
    const [resendingUuid, setResendingUuid] = useState<string | null>(null);
    const [pendingCancellation, setPendingCancellation] = useState<BookingResponse | null>(null);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');

    useEffect(() => {
        let active = true;
        getMyEvents()
            .then(async (myEvents) => {
                const manageableEvents = myEvents.filter((event) => event.role === 'EVENT_ADMIN');
                const bookingsByEvent = await Promise.all(manageableEvents.map((event) => getBookingsByEventId(event.id)));
                if (!active) return;
                setEvents(manageableEvents);
                setEventId(manageableEvents[0]?.id ?? null);
                setBookings(bookingsByEvent.flat());
            })
            .catch((requestError) => {
                if (active) setError(requestError instanceof Error ? requestError.message : 'Prenotazioni non disponibili.');
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, []);

    const runSearch = async () => {
        setLoading(true);
        setError('');
        setMessage('');

        try {
            let result: BookingResponse[];
            switch (mode) {
                case 'all':
                    result = (await Promise.all(events.map((event) => getBookingsByEventId(event.id)))).flat();
                    break;
                case 'event':
                    if (eventId === null) throw new Error('Seleziona un evento.');
                    result = await getBookingsByEventId(eventId);
                    break;
                case 'email':
                    if (!email.trim()) throw new Error('Inserisci un indirizzo email.');
                    result = (await Promise.all(events.map((event) => getBookingsByEventAndEmail(event.id, email.trim())))).flat();
                    break;
                case 'event-email':
                    if (eventId === null || !email.trim()) throw new Error('Seleziona un evento e inserisci un’email.');
                    result = await getBookingsByEventAndEmail(eventId, email);
                    break;
                case 'uuid':
                    if (!identifier.trim()) throw new Error('Inserisci l’UUID della prenotazione.');
                    result = [await getBookingByUUID(identifier)];
                    break;
                case 'id': {
                    const numericId = Number(identifier);
                    if (!Number.isInteger(numericId) || numericId <= 0) throw new Error('Inserisci un ID numerico valido.');
                    const booking = await getBookingById(numericId);
                    result = [booking];
                    break;
                }
            }
            setBookings(result);
        } catch (requestError) {
            setBookings([]);
            setError(requestError instanceof Error ? requestError.message : 'Ricerca non riuscita.');
        } finally {
            setLoading(false);
        }
    };

    const search = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        void runSearch();
    };

    const cancel = async (booking: BookingResponse) => {
        setCancelling(true);
        setError('');
        setMessage('');
        try {
            await cancelBooking(booking.uuid);
            await runSearch();
            setMessage(`Prenotazione di ${booking.name} ${booking.surname} annullata.`);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Annullamento non riuscito.');
        } finally {
            setCancelling(false);
            setPendingCancellation(null);
        }
    };

    const resendQr = async (booking: BookingResponse) => {
        setResendingUuid(booking.uuid);
        setError('');
        setMessage('');
        try {
            await resendBookingQr(booking.eventId, booking.uuid);
            setMessage(`Conferma QR per ${booking.name} ${booking.surname} in coda per l’invio a ${booking.email}.`);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Reinvio del QR non riuscito.');
        } finally {
            setResendingUuid(null);
        }
    };

    return (
        <section className="workspace-page">
            <div className="page-heading">
                <div>
                    <span className="eyebrow">Amministrazione / Prenotazioni</span>
                    <h1>Trova ogni pass<span className="accent-text">.</span></h1>
                    <p>Consulta, filtra e annulla le prenotazioni dei tuoi eventi.</p>
                </div>
                <button className="button" onClick={() => void runSearch()} disabled={loading}>
                    <RefreshCw size={16} className={loading ? 'spinning' : ''} /> Aggiorna
                </button>
            </div>

            <form className="panel filter-panel" onSubmit={search}>
                <label>
                    <span><Filter size={14} /> Vista</span>
                    <select value={mode} onChange={(event) => setMode(event.target.value as SearchMode)}>
                        {(Object.keys(modeLabels) as SearchMode[]).map((value) => <option value={value} key={value}>{modeLabels[value]}</option>)}
                    </select>
                </label>

                {(mode === 'event' || mode === 'event-email') && (
                    <label>
                        <span>Evento</span>
                        <select value={eventId ?? ''} onChange={(event) => setEventId(Number(event.target.value))} required>
                            {events.map((availableEvent) => <option key={availableEvent.id} value={availableEvent.id}>{availableEvent.name}</option>)}
                        </select>
                    </label>
                )}

                {(mode === 'email' || mode === 'event-email') && (
                    <label><span>Email</span><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
                )}

                {(mode === 'uuid' || mode === 'id') && (
                    <label>
                        <span>{mode === 'uuid' ? 'UUID' : 'ID interno'}</span>
                        <input value={identifier} onChange={(event) => setIdentifier(event.target.value)} inputMode={mode === 'id' ? 'numeric' : 'text'} required />
                    </label>
                )}

                <button className="button primary" disabled={loading}><Search size={16} /> Cerca</button>
            </form>

            {error && <div className="notice error" role="alert">{error}</div>}
            {message && <div className="notice success" role="status">{message}</div>}

            <div className="result-heading">
                <div><span className="eyebrow">Risultati</span><h2>{loading ? 'Caricamento…' : `${bookings.length} prenotazioni`}</h2></div>
            </div>

            {!loading && bookings.length === 0 ? (
                <div className="panel empty-state"><h2>Nessuna prenotazione trovata</h2><p>Modifica i filtri e riprova.</p></div>
            ) : (
                <div className="panel table-shell" aria-busy={loading}>
                    <table className="data-table">
                        <thead><tr><th>Cliente</th><th>Evento</th><th>Creata</th><th>Stato</th><th>UUID</th><th>Azioni</th></tr></thead>
                        <tbody>
                            {bookings.map((booking) => (
                                    <tr key={booking.uuid}>
                                        <td><strong>{booking.name} {booking.surname}</strong><small>{booking.email}{booking.phone ? ` · ${booking.phone}` : ''}</small></td>
                                        <td>{booking.eventName}<small>Evento #{booking.eventId}</small></td>
                                        <td>{formatDate(booking.createdAt)}</td>
                                        <td><span className={`state-badge booking-${booking.bookingStatus.toLowerCase()}`}>{statusLabels[booking.bookingStatus]}</span></td>
                                        <td><code>{booking.uuid}</code></td>
                                        <td>
                                            <div className="table-actions">
                                                <a className="icon-button" href={qrSource(booking.qrCodeBase64)} download={`PassHalo-${booking.uuid}.png`} aria-label="Scarica QR"><Download size={16} /></a>
                                                {booking.bookingStatus === 'CREATED' && events.some((event) => event.id === booking.eventId && event.owner && event.eventState !== 'FINISHED') && (
                                                    <button className="icon-button" onClick={() => void resendQr(booking)} disabled={resendingUuid !== null} title="Accoda un nuovo invio del QR" aria-label={`Reinvia il QR a ${booking.email}`}><Mail size={16} /></button>
                                                )}
                                                {booking.bookingStatus !== 'CANCELLED' && (
                                                    <button className="icon-button danger" onClick={() => setPendingCancellation(booking)} disabled={cancelling} aria-label={`Annulla la prenotazione di ${booking.name} ${booking.surname}`}><TicketX size={16} /></button>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
            <ConfirmDialog
                open={pendingCancellation !== null}
                title={pendingCancellation ? `Annullare la prenotazione di ${pendingCancellation.name} ${pendingCancellation.surname}?` : 'Annullare la prenotazione?'}
                description="Il pass non potrà più essere convalidato all’ingresso. L’operazione non può essere annullata."
                confirmLabel="Annulla prenotazione"
                busy={cancelling}
                onCancel={() => setPendingCancellation(null)}
                onConfirm={() => pendingCancellation ? cancel(pendingCancellation) : undefined}
            />
        </section>
    );
}
