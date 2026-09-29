import { useEffect, useState, type FormEvent } from 'react';
import { ArrowRight, Check, Download, Mail, Ticket } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { createBooking } from '../api/booking';
import { getEventById } from '../api/events';
import type { BookingResponse, Event } from '../types';
import { isEventBookable } from '../utils/eventAvailability';
import { bookingResendMailto } from '../utils/bookingResend';

function formatEventDate(value: string) {
    return new Intl.DateTimeFormat('it-IT', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

function formatMoney(value: number) {
    return value.toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
}

function qrSource(value: string) {
    return value.startsWith('data:') ? value : `data:image/png;base64,${value}`;
}

export function BookingEmbedPage() {
    const { eventId: eventIdParam } = useParams();
    const parsedEventId = Number(eventIdParam);
    const eventId = Number.isSafeInteger(parsedEventId) && parsedEventId > 0 ? parsedEventId : null;
    const [event, setEvent] = useState<Event | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [result, setResult] = useState<BookingResponse | null>(null);

    useEffect(() => {
        if (!eventId) return;
        let active = true;
        getEventById(eventId)
            .then((loadedEvent) => {
                if (!active) return;
                if (!isEventBookable(loadedEvent)) {
                    setError('Le prenotazioni per questo evento sono chiuse.');
                } else {
                    setEvent(loadedEvent);
                }
            })
            .catch((requestError) => {
                if (active) setError(requestError instanceof Error ? requestError.message : 'Evento non disponibile.');
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => { active = false; };
    }, [eventId]);

    useEffect(() => {
        let frame = 0;
        const reportHeight = () => {
            window.cancelAnimationFrame(frame);
            frame = window.requestAnimationFrame(() => {
                window.parent.postMessage({ type: 'passhalo:booking-embed:resize', height: document.documentElement.scrollHeight }, '*');
            });
        };
        const observer = new ResizeObserver(reportHeight);
        observer.observe(document.documentElement);
        window.addEventListener('load', reportHeight);
        reportHeight();
        return () => {
            observer.disconnect();
            window.removeEventListener('load', reportHeight);
            window.cancelAnimationFrame(frame);
        };
    }, []);

    async function submit(formEvent: FormEvent<HTMLFormElement>) {
        formEvent.preventDefault();
        if (!event) return;
        if (!isEventBookable(event)) {
            setError('Le prenotazioni per questo evento sono chiuse.');
            return;
        }
        const form = new FormData(formEvent.currentTarget);
        setBusy(true);
        setError('');
        try {
            setResult(await createBooking({
                name: String(form.get('name')).trim(),
                surname: String(form.get('surname')).trim(),
                email: String(form.get('email')).trim(),
                phone: String(form.get('phone')).trim(),
                marketingConsent: form.get('marketingConsent') === 'on',
                eventId: event.id,
            }));
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Prenotazione non riuscita. Riprova.');
        } finally {
            setBusy(false);
        }
    }

    if (result) {
        const qr = qrSource(result.qrCodeBase64);
        return (
            <main className="booking-embed" id="main-content">
                <section className="panel form-panel booking-embed-panel booking-embed-success">
                    <span className="status"><Check size={14} /> Prenotazione confermata</span>
                    <h1>Il tuo pass per {result.eventName}</h1>
                    <p>{result.name} {result.surname}<br />{result.email}</p>
                    <img className="qr-image" src={qr} alt="QR code personale per l’ingresso" />
                    <p className="form-note">Mostra questo codice all’ingresso e conservalo. Se l’email non arriva, controlla anche lo spam oppure prepara una richiesta a booking@passhalo.it.</p>
                    <a className="button primary full" href={qr} download={`PassHalo-${result.uuid}.png`}>
                        <Download size={17} /> Scarica il QR code
                    </a>
                    <a className="button full" href={bookingResendMailto(result.eventName, result.email)}>
                        <Mail size={17} /> Richiedi reinvio del codice QR
                    </a>
                    <p className="form-note">Si aprirà la tua app email: invia il messaggio per completare la richiesta.</p>
                </section>
            </main>
        );
    }

    if (!eventId) {
        return <main className="booking-embed" id="main-content"><div className="notice error" role="alert">Il link di prenotazione non è valido.</div></main>;
    }

    return (
        <main className="booking-embed" id="main-content">
            <section className="panel form-panel booking-embed-panel">
                {event && <header className="booking-embed-heading">
                    <span className="eyebrow"><Ticket size={14} /> Prenotazione evento</span>
                    <h1>{event.name}</h1>
                    <p>{formatEventDate(event.startDateTime)} · {event.location}</p>
                    <strong>{event.bookingPrice < event.normalPrice
                        ? `Con prenotazione ${formatMoney(event.bookingPrice)} invece di ${formatMoney(event.normalPrice)}`
                        : `Ingresso ${formatMoney(event.bookingPrice)}`}</strong>
                </header>}

                {error && <div className="notice error" role="alert">{error}</div>}
                {loading && <div className="notice" role="status">Caricamento evento…</div>}

                {event && <form onSubmit={submit}>
                    <div className="field-row">
                        <label>Nome<input name="name" autoComplete="given-name" required maxLength={100} pattern=".*\S.*" /></label>
                        <label>Cognome<input name="surname" autoComplete="family-name" required maxLength={100} pattern=".*\S.*" /></label>
                    </div>
                    <label>Email<input name="email" type="email" autoComplete="email" required maxLength={254} /></label>
                    <label>Telefono <span className="optional-label">Facoltativo</span><input name="phone" type="tel" autoComplete="tel" maxLength={40} /></label>
                    <label className="checkbox-label required-consent">
                        <input name="privacyAcknowledgement" type="checkbox" required />
                        <span>Ho letto l’<Link className="text-link" to={`/privacy?eventId=${event.id}`} target="_blank" rel="noreferrer">informativa privacy</Link>. <small>Obbligatorio</small></span>
                    </label>
                    <label className="checkbox-label">
                        <input name="marketingConsent" type="checkbox" />
                        <span>Desidero ricevere via email aggiornamenti sui prossimi eventi di {event.organizerName}. <small>Facoltativo</small></span>
                    </label>
                    <p className="form-note">Il QR appare subito dopo la conferma: scaricalo e conservalo.</p>
                    <button className="button primary full" disabled={busy || loading}>
                        {busy ? 'Creazione del pass…' : 'Prenota'} <ArrowRight size={17} />
                    </button>
                </form>}
                {event && <p className="form-note">Hai già prenotato ma non hai ricevuto il QR? <a className="text-link" href={bookingResendMailto(event.name)}>Richiedi il reinvio via email</a>.</p>}
                <p className="booking-embed-credit">Modulo prenotazioni gestito da <a href="/" target="_blank" rel="noreferrer">PassHalo</a></p>
            </section>
        </main>
    );
}
