import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ArrowRight, CalendarDays, KeyRound, RefreshCw, ScanLine } from 'lucide-react';
import { Link } from 'react-router-dom';
import { acceptInvitation } from '../api/collaboration';
import { getMyEvents, notifyEventAccessChanged } from '../api/events';
import type { MyEvent } from '../types';

const roleLabels = { EVENT_ADMIN: 'Amministratore evento', STAFF: 'Staff ingressi' };

function formatDate(value: string) {
    return new Intl.DateTimeFormat('it-IT', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

export function CollaborationsPage() {
    const [events, setEvents] = useState<MyEvent[]>([]);
    const [token, setToken] = useState('');
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');

    const refresh = useCallback(async () => {
        try {
            const myEvents = await getMyEvents();
            setError('');
            setEvents(myEvents);
            notifyEventAccessChanged();
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Collaborazioni non disponibili.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        let active = true;
        getMyEvents()
            .then((myEvents) => {
                if (!active) return;
                setEvents(myEvents);
            })
            .catch((requestError) => {
                if (active) setError(requestError instanceof Error ? requestError.message : 'Collaborazioni non disponibili.');
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, []);

    const sendToken = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setBusy(true);
        setError('');
        setMessage('');
        try {
            await acceptInvitation(token.trim());
            setToken('');
            setMessage('Invito accettato. L’evento è ora nella tua area di lavoro.');
            await refresh();
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Invito non valido o scaduto.');
        } finally {
            setBusy(false);
        }
    };

    return (
        <section className="workspace-page collaboration-page">
            <div className="page-heading">
                <div><span className="eyebrow">Area collaboratori</span><h1>I miei eventi<span className="accent-text">.</span></h1><p>Consulta gli eventi assegnati e accetta eventuali inviti come amministratore dell’evento.</p></div>
                <button className="button" onClick={() => { setLoading(true); void refresh(); }} disabled={loading}><RefreshCw size={16} className={loading ? 'spinning' : ''} /> Aggiorna</button>
            </div>
            {error && <div className="notice error" role="alert">{error}</div>}
            {message && <div className="notice success" role="status">{message}</div>}
            <div className="collaboration-forms single">
                <article className="panel collaboration-panel">
                    <span className="eyebrow"><KeyRound size={14} /> Invito amministratore</span>
                    <h2>Accetta un invito</h2>
                    <p>Se hai ricevuto un codice di invito via email, inseriscilo usando lo stesso account a cui è stato inviato.</p>
                    <form onSubmit={sendToken}><label htmlFor="invitation-token">Codice dell’invito</label><input id="invitation-token" value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" required /><button className="button" disabled={busy || !token.trim()}>{busy ? 'Verifica…' : 'Accetta invito'}</button></form>
                </article>
            </div>
            <div className="result-heading"><div><span className="eyebrow">Accessi attivi</span><h2>Eventi assegnati</h2></div></div>
            {!loading && events.length === 0 && <div className="panel empty-state"><CalendarDays size={28} /><h2>Nessun evento assegnato</h2><p>Gli eventi che puoi gestire compariranno qui.</p></div>}
            <div className="collaboration-event-list" aria-busy={loading}>
                {events.map((event) => <article className="panel collaboration-event" key={event.id}>
                    <div><span className="eyebrow">{event.owner ? 'Proprietario' : roleLabels[event.role]}</span><h3>{event.name}</h3><p>{formatDate(event.startDateTime)} · {event.location}</p></div>
                    <div className="collaboration-actions"><Link className="button primary" to={`${event.role === 'EVENT_ADMIN' ? '/admin/dashboard' : '/staff/scan'}?eventId=${event.id}`}>{event.role === 'EVENT_ADMIN' ? 'Gestisci' : 'Controlla ingressi'} <ArrowRight size={15} /></Link>{event.role === 'STAFF' && <ScanLine size={18} aria-hidden="true" />}</div>
                </article>)}
            </div>
        </section>
    );
}
