import { useEffect, useState, type FormEvent } from 'react';
import { ArrowDown, ArrowRight, CalendarDays, Check, Clock3, Download, MapPin, ShieldCheck, Ticket, UsersRound, WalletCards } from 'lucide-react';
import { Link } from 'react-router-dom';
import { createBooking } from '../api/booking';
import { getEvents } from '../api/events';
import { EventHeroMedia } from '../components/EventHeroMedia';
import type { BookingResponse, Event, EventFaq } from '../types';
import { isEventBookable } from '../utils/eventAvailability';

function formatEventDate(value: string) {
    return new Intl.DateTimeFormat('it-IT', {
        dateStyle: 'medium',
        timeStyle: 'short',
    }).format(new Date(value));
}

function formatEventDay(value: string) {
    return new Intl.DateTimeFormat('it-IT', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
    }).format(new Date(value));
}

function formatEventTime(value: string) {
    return new Intl.DateTimeFormat('it-IT', {
        hour: '2-digit',
        minute: '2-digit',
    }).format(new Date(value));
}

function formatMoney(value: number) {
    return value.toLocaleString('it-IT', { style: 'currency', currency: 'EUR' });
}

function qrSource(value: string) {
    return value.startsWith('data:') ? value : `data:image/png;base64,${value}`;
}

function getFaqs(event?: Event): EventFaq[] {
    const customFaqs = (event?.faqs ?? []).filter((faq) => faq.question.trim() && faq.answer.trim());
    if (customFaqs.length > 0) return customFaqs;
    if (!event) return [];
    const hasBookingDiscount = event.bookingPrice < event.normalPrice;

    return [
        {
            question: 'Devo pagare subito?',
            answer: `No. La prenotazione genera il pass e conferma il prezzo di ${formatMoney(event.bookingPrice)}. Pagherai all’ingresso secondo le modalità comunicate dall’organizzatore.`,
        },
        {
            question: 'Quanto costa l’ingresso?',
            answer: hasBookingDiscount
                ? `Con il pass PassHalo l’ingresso costa ${formatMoney(event.bookingPrice)} invece di ${formatMoney(event.normalPrice)}.`
                : `L’ingresso costa ${formatMoney(event.bookingPrice)} sia con prenotazione sia direttamente in cassa.`,
        },
        {
            question: 'Come funziona la prenotazione?',
            answer: 'Compila il modulo con i tuoi dati. Al termine riceverai subito un QR code personale da scaricare e mostrare all’ingresso.',
        },
        {
            question: 'Posso usare lo stesso QR per più persone?',
            answer: 'No. Ogni pass è personale e può essere convalidato una sola volta. Ogni partecipante deve effettuare la propria prenotazione.',
        },
        {
            question: 'Quanti posti sono disponibili?',
            answer: `La capienza indicata per l’evento è di ${event.totalTickets.toLocaleString('it-IT')} persone. Prenota in anticipo per assicurarti il posto.`,
        },
    ];
}

export function BookingPage() {
    const [events, setEvents] = useState<Event[]>([]);
    const [selectedEventId, setSelectedEventId] = useState<number | null>(null);
    const [eventsLoading, setEventsLoading] = useState(true);
    const [result, setResult] = useState<BookingResponse | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        const eventIdParameter = new URLSearchParams(window.location.search).get('eventId');
        const requestedEventId = eventIdParameter === null ? null : Number(eventIdParameter);

        getEvents()
            .then((availableEvents) => {
                if (!active) return;
                const bookableEvents = availableEvents.filter(isEventBookable);
                if (eventIdParameter !== null) {
                    const requested = Number.isInteger(requestedEventId) && requestedEventId !== null && requestedEventId > 0
                        ? bookableEvents.find((event) => event.id === requestedEventId)
                        : undefined;
                    setEvents(requested ? [requested] : []);
                    setSelectedEventId(requested?.id ?? null);
                    if (!requested) setError('Il link di prenotazione non è valido oppure l’evento non è più disponibile.');
                } else {
                    setEvents(bookableEvents);
                    setSelectedEventId(bookableEvents[0]?.id ?? null);
                }
            })
            .catch((requestError) => {
                if (active) setError(requestError instanceof Error ? requestError.message : 'Impossibile caricare gli eventi.');
            })
            .finally(() => {
                if (active) setEventsLoading(false);
            });

        return () => {
            active = false;
        };
    }, []);

    const selectedEvent = events.find((event) => event.id === selectedEventId);
    const hasBookingDiscount = selectedEvent !== undefined && selectedEvent.bookingPrice < selectedEvent.normalPrice;
    const faqs = getFaqs(selectedEvent);

    const selectEvent = (eventId: number) => {
        setSelectedEventId(eventId);
        const url = new URL(window.location.href);
        url.searchParams.set('eventId', String(eventId));
        window.history.replaceState(null, '', url);
    };

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (selectedEventId === null) {
            setError('Seleziona un evento prima di prenotare.');
            return;
        }
        const eventToBook = events.find((item) => item.id === selectedEventId);
        if (!eventToBook || !isEventBookable(eventToBook)) {
            setError('Le prenotazioni per questo evento sono chiuse.');
            return;
        }

        const form = new FormData(event.currentTarget);
        setBusy(true);
        setError('');

        try {
            setResult(await createBooking({
                name: String(form.get('name')).trim(),
                surname: String(form.get('surname')).trim(),
                email: String(form.get('email')).trim(),
                phone: String(form.get('phone')).trim(),
                marketingConsent: form.get('marketingConsent') === 'on',
                eventId: selectedEventId,
            }));
            window.scrollTo({ top: 0, behavior: 'smooth' });
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Prenotazione non riuscita. Riprova.');
        } finally {
            setBusy(false);
        }
    };

    if (result) {
        const qr = qrSource(result.qrCodeBase64);
        return (
            <section className="form-page public-confirmation-page">
                <div className="form-intro">
                    <span className="eyebrow"><Ticket size={15} /> Il tuo pass digitale</span>
                    <h1>Ci sei<span className="accent-text">.</span></h1>
                    <p>Prenotazione confermata. Conserva il tuo QR code.</p>
                </div>
                <div className="panel form-panel ticket-result">
                    <span className="status"><Check size={14} /> Prenotazione confermata</span>
                    <h2>{result.name} {result.surname}</h2>
                    <p>{result.eventName}<br />{result.email}</p>
                    <img className="qr-image" src={qr} alt="QR code da mostrare all’ingresso" />
                    <p className="form-note">Mostra questo codice al personale all’ingresso.</p>
                    <a className="button primary full" href={qr} download={`PassHalo-${result.uuid}.png`}>
                        <Download size={17} /> Scarica il QR code
                    </a>
                    <button className="button full" onClick={() => setResult(null)}>Un’altra prenotazione</button>
                </div>
            </section>
        );
    }

    return (
        <div className="public-event-page">
            <section className="public-event-hero" aria-labelledby="public-event-title">
                <EventHeroMedia
                    key={selectedEvent?.id ?? 'event-placeholder'}
                    eventName={selectedEvent?.name ?? 'PassHalo'}
                    imageUrl={selectedEvent?.imageUrl}
                    videoUrl={selectedEvent?.videoUrl}
                />
                <div className="public-hero-shade" aria-hidden="true" />

                <div className="public-hero-content">
                    <span className="public-event-kicker">
                        <Ticket size={15} />
                        {selectedEvent ? formatEventDay(selectedEvent.startDateTime) : 'Il tuo pass digitale'}
                    </span>
                    <h1 id="public-event-title">
                        {selectedEvent?.name ?? 'Entra nella serata'}<span className="accent-text">.</span>
                    </h1>
                    <p className="public-hero-description">
                        {selectedEvent?.description ?? 'Prenota il tuo ingresso e ricevi subito il QR code personale.'}
                    </p>

                    <div className="public-hero-actions">
                        <a className="button primary public-hero-cta" href="#booking-form">
                            Prenota il tuo posto <ArrowRight size={19} />
                        </a>
                        {selectedEvent && <a className="button public-hero-secondary" href="#event-details">Scopri l’evento</a>}
                    </div>

                    {selectedEvent && (
                        <ul className="public-hero-facts" aria-label="Informazioni principali dell’evento">
                            <li><CalendarDays size={19} /><span>Quando<strong>{formatEventDay(selectedEvent.startDateTime)}</strong></span></li>
                            <li><MapPin size={19} /><span>Dove<strong>{selectedEvent.location}</strong></span></li>
                            <li><Clock3 size={19} /><span>Orario<strong>{formatEventTime(selectedEvent.startDateTime)}</strong></span></li>
                            <li><WalletCards size={19} /><span>Con prenotazione<strong>{formatMoney(selectedEvent.bookingPrice)}</strong></span></li>
                        </ul>
                    )}

                    <p className="public-hero-assurance"><ShieldCheck size={15} /> Nessun account richiesto · QR immediato · Un pass per persona</p>
                </div>

                <a className="public-scroll-cue" href={selectedEvent ? '#event-details' : '#booking-form'} aria-label={selectedEvent ? 'Scopri i dettagli dell’evento' : 'Vai al modulo di prenotazione'}><ArrowDown size={19} /></a>
            </section>

            {selectedEvent && (
                <section className="public-event-story public-section" id="event-details" aria-labelledby="event-story-title">
                    <div className="public-story-copy">
                        <span className="eyebrow">L’esperienza</span>
                        <h2 id="event-story-title">Una serata da vivere, non solo da segnare in agenda<span className="accent-text">.</span></h2>
                        <p className="public-long-description">{selectedEvent.description}</p>

                        <div className="public-story-stats">
                            <div><UsersRound size={20} /><span>Capienza<strong>{selectedEvent.totalTickets.toLocaleString('it-IT')} posti</strong></span></div>
                            <div>
                                <WalletCards size={20} />
                                <span>
                                    {hasBookingDiscount ? 'Risparmio' : 'Prezzo ingresso'}
                                    <strong>{formatMoney(hasBookingDiscount ? selectedEvent.normalPrice - selectedEvent.bookingPrice : selectedEvent.bookingPrice)}</strong>
                                </span>
                            </div>
                        </div>
                    </div>

                    {selectedEvent.imageUrl && (
                        <figure className="public-event-image">
                            <img src={selectedEvent.imageUrl} alt={`Atmosfera di ${selectedEvent.name}`} loading="lazy" />
                            <figcaption>{selectedEvent.name} · {selectedEvent.location}</figcaption>
                        </figure>
                    )}
                </section>
            )}

            <section className="public-booking-section" id="booking-form" aria-labelledby="booking-title">
                <div className="public-booking-layout public-section">
                    <div className="public-booking-copy">
                        <span className="eyebrow">Assicurati il tuo posto</span>
                        <h2 id="booking-title">La prenotazione è gratuita. Il posto è tuo<span className="accent-text">.</span></h2>
                        <p>
                            {hasBookingDiscount
                                ? 'Prenota in meno di un minuto, scarica il QR e presentalo all’ingresso per ottenere il prezzo ridotto.'
                                : 'Prenota in meno di un minuto, scarica il QR e presentalo all’ingresso per accedere all’evento.'}
                        </p>
                        <ol className="public-booking-steps">
                            <li><span>01</span><div><strong>Inserisci i dati</strong><p>Servono solo nome, cognome ed email.</p></div></li>
                            <li><span>02</span><div><strong>Scarica il pass</strong><p>Il QR personale appare subito dopo la conferma.</p></div></li>
                            <li><span>03</span><div><strong>Mostralo all’ingresso</strong><p>Lo staff lo convaliderà una sola volta.</p></div></li>
                        </ol>
                    </div>

                    <div className="panel form-panel public-booking-form">
                        {error && <div className="notice error" role="alert">{error}</div>}

                        {events.length > 0 && (
                            <div className="event-choice">
                                {events.length > 1 && <><label htmlFor="event">Evento</label><select
                                    id="event"
                                    value={selectedEventId ?? ''}
                                    onChange={(event) => selectEvent(Number(event.target.value))}
                                    disabled={eventsLoading || busy}
                                >
                                    {events.map((availableEvent) => (
                                        <option key={availableEvent.id} value={availableEvent.id}>{availableEvent.name}</option>
                                    ))}
                                </select></>}
                                {events.length === 1 && <strong>{selectedEvent?.name}</strong>}
                                {selectedEvent && (
                                    <p className="event-meta">
                                        {formatEventDate(selectedEvent.startDateTime)} · {selectedEvent.location}<br />
                                        {hasBookingDiscount
                                            ? <>Ingresso ridotto: {formatMoney(selectedEvent.bookingPrice)} invece di {formatMoney(selectedEvent.normalPrice)}</>
                                            : <>Ingresso: {formatMoney(selectedEvent.bookingPrice)}</>}
                                    </p>
                                )}
                            </div>
                        )}

                        {eventsLoading && <div className="notice" role="status">Caricamento eventi…</div>}
                        {!eventsLoading && events.length === 0 && !error && <div className="notice error" role="alert">Non ci sono eventi disponibili per la prenotazione.</div>}

                        <div className="form-section-title"><span>01 / I tuoi dati</span><span>Telefono facoltativo</span></div>
                        <form onSubmit={submit}>
                            <div className="field-row">
                                <label>Nome<input name="name" autoComplete="given-name" placeholder="Il tuo nome" required maxLength={100} pattern=".*\S.*" /></label>
                                <label>Cognome<input name="surname" autoComplete="family-name" placeholder="Il tuo cognome" required maxLength={100} pattern=".*\S.*" /></label>
                            </div>
                            <label>Email<input name="email" type="email" autoComplete="email" placeholder="nome@esempio.it" required /></label>
                            <label>Telefono <span className="optional-label">Facoltativo</span><input name="phone" type="tel" autoComplete="tel" placeholder="+39 333 1234567" /></label>
                            <label className="checkbox-label required-consent">
                                <input key={`privacy-${selectedEventId}`} name="privacyAcknowledgement" type="checkbox" required />
                                <span>Ho letto l’<Link className="text-link" to={selectedEvent ? `/privacy?eventId=${selectedEvent.id}` : '/privacy'} target="_blank" rel="noreferrer">informativa privacy</Link> sul trattamento dei dati. <small>Obbligatorio</small></span>
                            </label>
                            <label className="checkbox-label"><input key={`marketing-${selectedEventId}`} name="marketingConsent" type="checkbox" /><span>Desidero ricevere via email aggiornamenti sui prossimi eventi di {selectedEvent?.organizerName ?? 'questo organizzatore'}. <small>Facoltativo</small></span></label>
                            <p className="form-note">I dati della prenotazione servono a generare il pass e verificare il tuo ingresso.</p>
                            <button className="button primary full" disabled={busy || eventsLoading || selectedEventId === null}>
                                {busy ? 'Creazione del pass…' : 'Ottieni il tuo pass'} <ArrowRight size={18} />
                            </button>
                        </form>
                        <div className="form-bottom"><Ticket size={16} /><span>Un pass personale. Un ingresso più semplice.</span></div>
                    </div>
                </div>
            </section>

            {faqs.length > 0 && (
                <section className="public-faq-section public-section" aria-labelledby="faq-title">
                    <div className="public-faq-heading">
                        <span className="eyebrow">Domande frequenti</span>
                        <h2 id="faq-title">Prima di prenotare<span className="accent-text">.</span></h2>
                        <p>Tutto quello che serve sapere per arrivare preparati.</p>
                    </div>
                    <div className="public-faq-list">
                        {faqs.map((faq, index) => (
                            <details key={`${faq.question}-${index}`}>
                                <summary><span>{String(index + 1).padStart(2, '0')}</span>{faq.question}</summary>
                                <p>{faq.answer}</p>
                            </details>
                        ))}
                    </div>
                </section>
            )}

            <section className="public-final-cta">
                <span className="eyebrow">PassHalo</span>
                <h2>Ci vediamo sotto il palco<span className="accent-text">.</span></h2>
                <a className="button primary" href="#booking-form">Prenota ora <ArrowRight size={18} /></a>
            </section>
        </div>
    );
}
