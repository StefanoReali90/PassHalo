import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { CheckCircle2, Keyboard, Minus, Plus, QrCode, Wifi, WifiOff, XCircle } from 'lucide-react';
import { checkInBooking } from '../api/booking';
import { decrementWalkInCount, getMyEvents, incrementWalkInCount } from '../api/events';
import { QrCameraScanner } from '../components/QrCameraScanner';
import type { MyEvent } from '../types';

interface ScanHistoryItem {
    id: number;
    status: 'success' | 'error';
    description: string;
    time: string;
}

const uuidPattern = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;

function extractUuid(value: string) {
    return value.match(uuidPattern)?.[0] ?? value.trim();
}

function focusScannerInput(input: HTMLInputElement | null) {
    if (!input || !window.matchMedia('(pointer: fine)').matches) return;
    input.focus({ preventScroll: true });
}

export function StaffScanPage() {
    const [token, setToken] = useState('');
    const [busy, setBusy] = useState(false);
    const [online, setOnline] = useState(() => navigator.onLine);
    const [success, setSuccess] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [history, setHistory] = useState<ScanHistoryItem[]>([]);
    const [events, setEvents] = useState<MyEvent[]>([]);
    const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
    const [eventsLoading, setEventsLoading] = useState(true);
    const [eventsError, setEventsError] = useState('');
    const [walkInBusy, setWalkInBusy] = useState(false);
    const [walkInMessage, setWalkInMessage] = useState('');
    const [sessionWalkIns, setSessionWalkIns] = useState<Record<number, number>>({});
    const inputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        const goOnline = () => setOnline(true);
        const goOffline = () => setOnline(false);
        window.addEventListener('online', goOnline);
        window.addEventListener('offline', goOffline);
        return () => {
            window.removeEventListener('online', goOnline);
            window.removeEventListener('offline', goOffline);
        };
    }, []);

    useEffect(() => {
        focusScannerInput(inputRef.current);
    }, []);

    useEffect(() => {
        let active = true;
        getMyEvents()
            .then((myEvents) => {
                if (!active) return;
                const operational = myEvents.filter((event) => event.eventState !== 'FINISHED');
                setEvents(operational);
                const requestedEventId = Number(new URLSearchParams(window.location.search).get('eventId'));
                setSelectedEventId(operational.find((event) => event.id === requestedEventId)?.id ?? operational[0]?.id ?? null);
            })
            .catch((requestError) => {
                if (active) setEventsError(requestError instanceof Error ? requestError.message : 'Eventi assegnati non disponibili.');
            })
            .finally(() => { if (active) setEventsLoading(false); });
        return () => { active = false; };
    }, []);

    const addHistory = useCallback((status: ScanHistoryItem['status'], description: string) => {
        setHistory((current) => [{
            id: Date.now(),
            status,
            description,
            time: new Intl.DateTimeFormat('it-IT', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(new Date()),
        }, ...current].slice(0, 5));
    }, []);

    const validatePass = useCallback(async (rawValue: string) => {
        const uuid = extractUuid(rawValue);
        if (!uuid || busy || !online || events.length === 0) return;

        setBusy(true);
        setSuccess(null);
        setError(null);
        try {
            const result = await checkInBooking(uuid);
            const description = `Pass convalidato · ${result.eventName}`;
            setSuccess(description);
            setToken('');
            addHistory('success', description);
            navigator.vibrate?.(100);
        } catch (requestError) {
            const description = requestError instanceof Error ? requestError.message : 'Codice non valido o già utilizzato.';
            setError(description);
            addHistory('error', description);
            navigator.vibrate?.([80, 60, 80]);
        } finally {
            setBusy(false);
            window.setTimeout(() => focusScannerInput(inputRef.current), 0);
        }
    }, [addHistory, busy, online, events.length]);

    const adjustWalkIns = async (direction: 'increment' | 'decrement') => {
        if (selectedEventId === null || walkInBusy || !online) return;
        setWalkInBusy(true);
        setEventsError('');
        setWalkInMessage('');
        try {
            if (direction === 'increment') await incrementWalkInCount(selectedEventId);
            else await decrementWalkInCount(selectedEventId);
            setSessionWalkIns((current) => ({ ...current, [selectedEventId]: (current[selectedEventId] ?? 0) + (direction === 'increment' ? 1 : -1) }));
            setWalkInMessage(direction === 'increment' ? 'Ingresso senza prenotazione registrato.' : 'Ultimo ingresso senza prenotazione rimosso.');
        } catch (requestError) {
            setEventsError(requestError instanceof Error ? requestError.message : 'Conteggio ingressi non aggiornato.');
        } finally { setWalkInBusy(false); }
    };

    const check = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        void validatePass(token);
    };

    return (
        <section className="scan-page">
            <div className="scan-heading">
                <div>
                    <span className="eyebrow"><QrCode size={15} /> Controllo ingressi</span>
                    <h1>Un pass. Un ingresso.</h1>
                    <p>Scansiona il QR con la fotocamera oppure usa un lettore collegato alla tastiera.</p>
                </div>
                <span className={`connection-status ${online ? 'online' : 'offline'}`} role="status">
                    {online ? <Wifi size={15} /> : <WifiOff size={15} />}{online ? 'Connesso' : 'Senza connessione'}
                </span>
            </div>

            {!online && <div className="notice error" role="alert"><WifiOff size={17} />Il check-in richiede una connessione al server per impedire riutilizzi dello stesso QR.</div>}
            {eventsError && <div className="notice error" role="alert">{eventsError}</div>}
            {!eventsLoading && events.length === 0 && <div className="notice error" role="alert">Non hai eventi attivi assegnati.</div>}

            <div className="scan-grid">
                <article className="panel scan-panel">
                    {success && (
                        <div className="scan-result success" role="status">
                            <CheckCircle2 size={34} />
                            <div><strong>INGRESSO OK</strong><span>{success}</span></div>
                        </div>
                    )}
                    {error && (
                        <div className="scan-result error" role="alert">
                            <XCircle size={34} />
                            <div><strong>INGRESSO KO</strong><span>{error}</span></div>
                        </div>
                    )}

                    <QrCameraScanner disabled={busy || !online || eventsLoading || events.length === 0} onDetected={validatePass} />

                    <div className="scan-divider"><span>oppure</span></div>
                    <form onSubmit={check}>
                        <label htmlFor="pass-code"><Keyboard size={15} /> Codice del pass</label>
                        <div className="scan-input-row">
                            <input
                                ref={inputRef}
                                id="pass-code"
                                value={token}
                                onChange={(event) => setToken(event.target.value)}
                                placeholder="UUID del biglietto"
                                autoComplete="off"
                                required
                            />
                            <button className="button primary" disabled={busy || !online || eventsLoading || events.length === 0 || !token.trim()}>{busy ? 'Verifica…' : 'Convalida'}</button>
                        </div>
                    </form>
                </article>

                <aside className="panel scan-history" aria-labelledby="scan-history-title">
                    <div><span className="eyebrow">Sessione corrente</span><h2 id="scan-history-title">Ultimi controlli</h2></div>
                    {history.length === 0 ? <p>Nessun pass controllato in questa sessione.</p> : (
                        <ol>
                            {history.map((item) => (
                                <li key={item.id} className={item.status}>
                                    {item.status === 'success' ? <CheckCircle2 size={16} /> : <XCircle size={16} />}
                                    <div><strong>{item.status === 'success' ? 'OK' : 'KO'}</strong><span>{item.description}</span></div>
                                    <time>{item.time}</time>
                                </li>
                            ))}
                        </ol>
                    )}
                    <p className="scan-role-note">La verifica mostra soltanto l’esito e l’evento, senza dati personali del cliente.</p>
                    <div className="walk-in-tools">
                        <span className="eyebrow">Ingressi senza prenotazione</span>
                        <h2>Registra gli accessi in cassa</h2>
                        <label htmlFor="walk-in-event">Evento</label>
                        <select id="walk-in-event" value={selectedEventId ?? ''} onChange={(event) => {
                            const eventId = Number(event.target.value);
                            setSelectedEventId(eventId);
                            setWalkInMessage('');
                            const url = new URL(window.location.href);
                            url.searchParams.set('eventId', String(eventId));
                            window.history.replaceState(null, '', url);
                        }} disabled={eventsLoading || events.length === 0}>
                            {events.map((event) => <option key={event.id} value={event.id}>{event.name}</option>)}
                        </select>
                        <p>Registrati da questo dispositivo durante la sessione: <strong>{selectedEventId === null ? 0 : sessionWalkIns[selectedEventId] ?? 0}</strong></p>
                        <div className="counter-actions">
                            <button className="button" disabled={walkInBusy || !online || selectedEventId === null || (sessionWalkIns[selectedEventId] ?? 0) <= 0} onClick={() => void adjustWalkIns('decrement')}><Minus size={16} /> Correggi</button>
                            <button className="button primary" disabled={walkInBusy || !online || selectedEventId === null} onClick={() => void adjustWalkIns('increment')}><Plus size={16} /> Aggiungi ingresso</button>
                        </div>
                        {walkInMessage && <p className="notice success" role="status">{walkInMessage}</p>}
                    </div>
                </aside>
            </div>
        </section>
    );
}
