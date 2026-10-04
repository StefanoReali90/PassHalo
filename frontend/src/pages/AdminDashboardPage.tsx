import { useEffect, useState } from 'react';
import { ArrowUpRight, BarChart3, Minus, Plus, RefreshCw, ScanLine, Ticket, Users, Wallet } from 'lucide-react';
import { Link } from 'react-router-dom';
import { BookingShare } from '../components/BookingShare';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EventComparisonChart, type EventComparisonItem } from '../components/EventComparisonChart';
import { EventResultsCharts } from '../components/EventResultsCharts';
import { PaymentCounts } from '../components/PaymentCounts';
import { PaymentMethodSelector } from '../components/PaymentMethodSelector';
import { paymentMethodLabel } from '../utils/paymentMethod';
import { closeEvent, decrementWalkInCount, getEventDashboard, getMyEvents, incrementWalkInCount } from '../api/events';
import type { EventDashboardResponse, MyEvent, PaymentMethod } from '../types';

const number = (value: number) => value.toLocaleString('it-IT');
const money = (value: number) => value.toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });

export function AdminDashboardPage() {
    const [events, setEvents] = useState<MyEvent[]>([]);
    const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
    const [data, setData] = useState<EventDashboardResponse | null>(null);
    const [busy, setBusy] = useState(true);
    const [error, setError] = useState('');
    const [closing, setClosing] = useState(false);
    const [walkInBusy, setWalkInBusy] = useState(false);
    const [walkInMethod, setWalkInMethod] = useState<PaymentMethod | null>(null);
    const [message, setMessage] = useState('');
    const [comparisonItems, setComparisonItems] = useState<EventComparisonItem[]>([]);
    const [comparisonVisible, setComparisonVisible] = useState(false);
    const [comparisonLoading, setComparisonLoading] = useState(false);
    const [comparisonError, setComparisonError] = useState('');
    const [closeDialogOpen, setCloseDialogOpen] = useState(false);

    useEffect(() => {
        let active = true;
        getMyEvents()
            .then(async (myEvents) => {
                if (!active) return;
                const manageableEvents = myEvents.filter((event) => event.role === 'EVENT_ADMIN');
                setEvents(manageableEvents);
                const requestedEventId = Number(new URLSearchParams(window.location.search).get('eventId'));
                const firstEventId = manageableEvents.find((event) => event.id === requestedEventId)?.id ?? manageableEvents[0]?.id ?? null;
                setSelectedEventId(firstEventId);
                if (firstEventId === null) {
                    setError('Non hai eventi da amministrare.');
                    return;
                }
                const dashboard = await getEventDashboard(firstEventId);
                if (active) setData(dashboard);
            })
            .catch((requestError) => {
                if (active) setError(requestError instanceof Error ? requestError.message : 'Statistiche non disponibili.');
            })
            .finally(() => {
                if (active) setBusy(false);
            });

        return () => {
            active = false;
        };
    }, []);

    const loadDashboard = async (eventId: number) => {
        setBusy(true);
        setError('');
        try {
            setData(await getEventDashboard(eventId));
        } catch (requestError) {
            setData(null);
            setError(requestError instanceof Error ? requestError.message : 'Statistiche non disponibili.');
        } finally {
            setBusy(false);
        }
    };

    const refresh = async () => {
        if (selectedEventId === null) return;
        setMessage('');
        await loadDashboard(selectedEventId);
        if (comparisonVisible) {
            setComparisonItems([]);
            setComparisonVisible(false);
        }
    };

    const selectEvent = (eventId: number) => {
        setSelectedEventId(eventId);
        setWalkInMethod(null);
        const url = new URL(window.location.href);
        url.searchParams.set('eventId', String(eventId));
        window.history.replaceState(null, '', url);
        setData(null);
        setMessage('');
        void loadDashboard(eventId);
    };

    const close = async () => {
        if (selectedEventId === null) return;

        setClosing(true);
        setError('');
        try {
            await closeEvent(selectedEventId);
            setEvents((current) => current.map((event) => event.id === selectedEventId ? { ...event, eventState: 'FINISHED' } : event));
            await loadDashboard(selectedEventId);
            setComparisonItems([]);
            setComparisonVisible(false);
            setMessage('Evento chiuso. Dati personali anonimizzati e risultati conservati.');
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Chiusura non riuscita.');
        } finally {
            setClosing(false);
            setCloseDialogOpen(false);
        }
    };

    const adjustWalkIns = async (direction: 'increment' | 'decrement') => {
        if (selectedEventId === null || walkInMethod === null || walkInBusy || busy || closing) return;
        setWalkInBusy(true);
        setError('');
        setMessage('');
        try {
            if (direction === 'increment') await incrementWalkInCount(selectedEventId, walkInMethod);
            else await decrementWalkInCount(selectedEventId, walkInMethod);
            await loadDashboard(selectedEventId);
            setComparisonItems([]);
            setComparisonVisible(false);
            setMessage(`${direction === 'increment' ? 'Ingresso in cassa registrato' : 'Ingresso in cassa rimosso'} · ${paymentMethodLabel(walkInMethod)}.`);
            setWalkInMethod(null);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Aggiornamento ingressi non riuscito.');
        } finally {
            setWalkInBusy(false);
        }
    };

    const showComparison = async () => {
        if (comparisonVisible) {
            setComparisonVisible(false);
            return;
        }
        if (comparisonItems.length >= 2) {
            setComparisonVisible(true);
            return;
        }

        setComparisonLoading(true);
        setComparisonError('');
        const candidates = [...events]
            .sort((first, second) => new Date(second.startDateTime).getTime() - new Date(first.startDateTime).getTime())
            .slice(0, 6);
        try {
            const results = await Promise.allSettled(candidates.map((event) => getEventDashboard(event.id)));
            const available = results.flatMap((result, index) => result.status === 'fulfilled'
                ? [{ event: candidates[index], dashboard: result.value }]
                : []);
            if (available.length < 2) throw new Error('Servono almeno due eventi con statistiche disponibili per il confronto.');
            setComparisonItems(available);
            setComparisonVisible(true);
        } catch (requestError) {
            setComparisonError(requestError instanceof Error ? requestError.message : 'Confronto eventi non disponibile.');
        } finally {
            setComparisonLoading(false);
        }
    };

    const selectedEvent = events.find((event) => event.id === selectedEventId);
    const eventName = selectedEvent?.name ?? data?.eventName ?? 'Evento principale';
    const isFinished = selectedEvent?.eventState === 'FINISHED';
    const attendanceRate = data?.attendanceRate ?? 0;

    return (
        <section className="dashboard">
            <div className="page-heading">
                <div>
                    <span className="eyebrow">Workspace / Risultati</span>
                    <h1>Il tuo evento, a colpo d’occhio<span className="accent-text">.</span></h1>
                    <p>Presenze, conversione, capienza e ricavi basati sui dati reali dell’evento.</p>
                </div>
                <button className="button" onClick={() => void refresh()} disabled={busy || selectedEventId === null}>
                    <RefreshCw size={16} className={busy ? 'spinning' : ''} /> Aggiorna
                </button>
            </div>

            <div className="event-strip">
                <div>
                    <span className="event-icon"><Ticket size={22} /></span>
                    <div>
                        <span className="eyebrow">Evento selezionato</span>
                        {events.length > 1 ? (
                            <select className="event-selector" value={selectedEventId ?? ''} disabled={walkInBusy || closing || busy} onChange={(event) => selectEvent(Number(event.target.value))} aria-label="Seleziona evento">
                                {events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}
                            </select>
                        ) : <strong>{eventName}</strong>}
                    </div>
                </div>
                <div className="event-strip-actions">
                    {events.length > 1 && <button className="button" onClick={() => void showComparison()} disabled={comparisonLoading}><BarChart3 size={16} />{comparisonLoading ? 'Confronto…' : comparisonVisible ? 'Nascondi confronto' : 'Confronta eventi'}</button>}
                    <Link className="button" to="/admin/events">Gestisci evento</Link>
                    <a className="button" href="#prenotazioni">Form prenotazioni <ArrowUpRight size={16} /></a>
                </div>
            </div>

            {error && <div className="notice error" role="alert">{error}</div>}
            {message && <div className="notice success" role="status">{message}</div>}
            {comparisonError && <div className="notice error" role="alert">{comparisonError}</div>}

            {!data || !selectedEvent ? (
                <div className="panel empty-state" role="status">
                    <h2>{busy ? 'Caricamento delle statistiche…' : 'Le statistiche non sono disponibili'}</h2>
                    <p>{busy ? 'Stiamo recuperando i dati del tuo evento.' : 'Verifica la connessione oppure crea il tuo primo evento.'}</p>
                    {!busy && events.length === 0 && <Link className="button primary" to="/admin/events">Crea un evento</Link>}
                </div>
            ) : (
                <>
                    <div className="metrics">
                        {[
                            { label: 'Prenotazioni', value: number(data.totalBookings), note: 'Pass attivi richiesti online', icon: Ticket },
                            { label: 'Ingressi totali', value: number(data.totalAttendees), note: `${number(data.checkedInCount)} QR + ${number(data.walkInCount)} in cassa`, icon: Users },
                            { label: 'Partecipazione', value: `${attendanceRate.toFixed(1).replace('.0', '')}%`, note: `${number(data.noShowCount)} non presentati`, icon: ScanLine },
                            { label: 'Ricavo effettivo', value: money(data.totalRevenue), note: 'Ingressi realmente registrati', icon: Wallet },
                        ].map(({ label, value, note, icon: Icon }) => (
                            <article className="panel metric" key={label}>
                                <div>{label}<Icon size={17} /></div>
                                <strong>{value}</strong>
                                <small>{note}</small>
                            </article>
                        ))}
                    </div>

                    <EventResultsCharts data={data} event={selectedEvent} />
                    <PaymentCounts data={data} />
                    {comparisonVisible && comparisonItems.length >= 2 && <EventComparisonChart items={comparisonItems} />}

                    <BookingShare eventId={selectedEventId} />

                    <div className="bottom-grid">
                        <article className="panel operations walk-in-operation">
                            <span className="eyebrow">Ingressi senza pass</span>
                            <h2>Ingressi in cassa: {number(data.walkInCount)}</h2>
                            <p>Seleziona il metodo per aggiungere un ingresso o correggere il relativo conteggio.</p>
                            <PaymentMethodSelector value={walkInMethod} onChange={setWalkInMethod} disabled={walkInBusy || busy || closing || isFinished} />
                            <div className="counter-actions">
                                <button className="button" disabled={walkInBusy || busy || closing || walkInMethod === null || (walkInMethod === 'CASH' ? data.walkInCashCount : data.walkInCardCount) <= 0 || isFinished} onClick={() => void adjustWalkIns('decrement')}><Minus size={17} /> Rimuovi</button>
                                <button className="button primary" disabled={walkInBusy || busy || closing || walkInMethod === null || isFinished} onClick={() => void adjustWalkIns('increment')}><Plus size={17} /> Aggiungi ingresso</button>
                            </div>
                        </article>
                        <article className="panel operations">
                            <span className="eyebrow">Operatività</span>
                            <h2>Pronto ad aprire le porte?</h2>
                            <p>Apri il controllo ingressi per convalidare i pass e registrare gli accessi.</p>
                            <div className="operation-actions"><Link className="button primary" to="/staff/scan">Controllo ingressi <ArrowUpRight size={17} /></Link><Link className="button" to="/admin/bookings">Prenotazioni</Link></div>
                        </article>
                        <article className="panel operations">
                            <span className="eyebrow">Fine evento</span>
                            <h2>Chiudi l’evento</h2>
                            <p>Anonimizza i dati personali e conserva le statistiche aggregate. L’operazione è definitiva.</p>
                            <button className="button danger" disabled={closing || isFinished} onClick={() => setCloseDialogOpen(true)}>{closing ? 'Chiusura…' : isFinished ? 'Evento chiuso' : 'Chiudi evento'}</button>
                        </article>
                    </div>
                </>
            )}
            <ConfirmDialog
                open={closeDialogOpen}
                title={`Chiudere “${eventName}”?`}
                description="I dati personali saranno anonimizzati e resteranno soltanto le statistiche aggregate. L’operazione è definitiva."
                confirmLabel="Chiudi evento"
                busy={closing}
                onCancel={() => setCloseDialogOpen(false)}
                onConfirm={close}
            />
        </section>
    );
}
