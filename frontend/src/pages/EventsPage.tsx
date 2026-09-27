import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ArrowUpRight, CalendarDays, Check, CircleHelp, Copy, Edit3, Film, MapPin, Plus, RefreshCw, Trash2, UsersRound, X } from 'lucide-react';
import { createEvent, deleteEvent, getEventById, getMyEvents, updateEvent } from '../api/events';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { useAuth } from '../context/useAuth';
import { Link } from 'react-router-dom';
import type { Event, EventFaq, EventRequest, EventState, MyEvent } from '../types';

interface EventFormState {
    name: string;
    description: string;
    location: string;
    start: string;
    end: string;
    imageUrl: string;
    videoUrl: string;
    faqs: EventFaq[];
    totalTickets: string;
    normalPrice: string;
    bookingPrice: string;
}

const emptyForm: EventFormState = {
    name: '',
    description: '',
    location: '',
    start: '',
    end: '',
    imageUrl: '',
    videoUrl: '',
    faqs: [],
    totalTickets: '',
    normalPrice: '',
    bookingPrice: '',
};

const stateLabels: Record<EventState, string> = {
    WAITING: 'In attesa',
    IN_PROGRESS: 'In corso',
    FINISHED: 'Concluso',
};

function toDateTimeInput(value: string) {
    return value ? value.slice(0, 16) : '';
}

function formatDate(value: string) {
    return new Intl.DateTimeFormat('it-IT', {
        dateStyle: 'medium',
        timeStyle: 'short',
    }).format(new Date(value));
}

function eventToForm(event: Event): EventFormState {
    return {
        name: event.name,
        description: event.description,
        location: event.location,
        start: toDateTimeInput(event.startDateTime),
        end: toDateTimeInput(event.endDateTime),
        imageUrl: event.imageUrl,
        videoUrl: event.videoUrl ?? '',
        faqs: (event.faqs ?? []).map((faq) => ({ ...faq })),
        totalTickets: String(event.totalTickets),
        normalPrice: String(event.normalPrice),
        bookingPrice: String(event.bookingPrice),
    };
}

export function EventsPage() {
    const { user } = useAuth();
    const canCreate = user?.role === 'ADMIN';
    const [events, setEvents] = useState<MyEvent[]>([]);
    const [form, setForm] = useState<EventFormState>(emptyForm);
    const [editingId, setEditingId] = useState<number | null>(null);
    const [presentationDirty, setPresentationDirty] = useState(false);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [loadingEditor, setLoadingEditor] = useState(false);
    const [deletingId, setDeletingId] = useState<number | null>(null);
    const [eventToDelete, setEventToDelete] = useState<Event | null>(null);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [copiedEmbedId, setCopiedEmbedId] = useState<number | null>(null);

    const copyEmbedCode = async (event: Event) => {
        const escapedTitle = event.name.replace(/[&<>"']/g, (character) => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
        })[character] ?? character);
        const embedUrl = new URL(`/embed/booking/${event.id}`, window.location.origin).toString();
        const frameId = `passhalo-booking-${event.id}`;
        const trustedOrigin = window.location.origin;
        const code = `<iframe id="${frameId}" src="${embedUrl}" title="Prenotazione: ${escapedTitle}" width="100%" height="860" style="width:100%;min-height:680px;border:0" loading="lazy" referrerpolicy="strict-origin-when-cross-origin"></iframe>\n<script>(function(){var frame=document.getElementById("${frameId}");var origin="${trustedOrigin}";window.addEventListener("message",function(event){if(event.origin!==origin||event.source!==frame.contentWindow||!event.data||event.data.type!=="passhalo:booking-embed:resize")return;var height=Number(event.data.height);if(Number.isFinite(height)&&height>=360&&height<=1600)frame.style.height=height+"px";});})();</script>`;
        try {
            await navigator.clipboard.writeText(code);
            setCopiedEmbedId(event.id);
            window.setTimeout(() => setCopiedEmbedId((current) => current === event.id ? null : current), 2500);
        } catch {
            window.prompt('Copia e incolla questo codice nel widget HTML di Elementor:', code);
        }
    };

    const loadEvents = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            setEvents((await getMyEvents()).filter((event) => event.role === 'EVENT_ADMIN'));
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Impossibile caricare gli eventi.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        let active = true;
        getMyEvents()
            .then((manageableEvents) => {
                if (active) setEvents(manageableEvents.filter((event) => event.role === 'EVENT_ADMIN'));
            })
            .catch((requestError) => {
                if (active) setError(requestError instanceof Error ? requestError.message : 'Impossibile caricare gli eventi.');
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, []);

    const updateField = (field: Exclude<keyof EventFormState, 'faqs'>, value: string) => {
        setForm((current) => ({ ...current, [field]: value }));
    };

    const addFaq = () => {
        setPresentationDirty(true);
        setForm((current) => ({
            ...current,
            faqs: [...current.faqs, { question: '', answer: '' }],
        }));
    };

    const updateFaq = (index: number, field: keyof EventFaq, value: string) => {
        setPresentationDirty(true);
        setForm((current) => ({
            ...current,
            faqs: current.faqs.map((faq, faqIndex) => faqIndex === index ? { ...faq, [field]: value } : faq),
        }));
    };

    const removeFaq = (index: number) => {
        setPresentationDirty(true);
        setForm((current) => ({
            ...current,
            faqs: current.faqs.filter((_, faqIndex) => faqIndex !== index),
        }));
    };

    const resetEditor = () => {
        setEditingId(null);
        setPresentationDirty(false);
        setForm(emptyForm);
        setError('');
    };

    const edit = async (eventId: number) => {
        setLoadingEditor(true);
        setError('');
        setMessage('');
        try {
            const event = await getEventById(eventId);
            setEditingId(event.id);
            setPresentationDirty(false);
            setForm(eventToForm(event));
            document.getElementById('event-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Evento non disponibile.');
        } finally {
            setLoadingEditor(false);
        }
    };

    const submit = async (submitEvent: FormEvent<HTMLFormElement>) => {
        submitEvent.preventDefault();
        setSaving(true);
        setError('');
        setMessage('');

        const videoUrl = form.videoUrl.trim();
        const faqs = form.faqs.map((faq) => ({
            question: faq.question.trim(),
            answer: faq.answer.trim(),
        }));
        const payload: EventRequest = {
            name: form.name.trim(),
            description: form.description.trim(),
            location: form.location.trim(),
            start: form.start,
            end: form.end,
            imageUrl: form.imageUrl.trim(),
            ...(videoUrl ? { videoUrl } : presentationDirty && editingId !== null ? { videoUrl: null } : {}),
            ...(faqs.length > 0 ? { faqs } : presentationDirty && editingId !== null ? { faqs: [] } : {}),
            totalTickets: Number(form.totalTickets),
            normalPrice: Number(form.normalPrice),
            bookingPrice: Number(form.bookingPrice),
        };

        if (new Date(payload.start) >= new Date(payload.end)) {
            setError('La fine dell’evento deve essere successiva all’inizio.');
            setSaving(false);
            return;
        }
        if (payload.bookingPrice > payload.normalPrice) {
            setError('Il prezzo prenotazione non può superare il prezzo in cassa.');
            setSaving(false);
            return;
        }

        try {
            if (editingId === null && canCreate) {
                await createEvent(payload);
                setMessage('Evento creato con successo.');
            } else if (editingId !== null) {
                await updateEvent(editingId, payload);
                setMessage('Evento aggiornato con successo.');
            } else {
                throw new Error('Non puoi creare un nuovo evento con questo account.');
            }
            setEditingId(null);
            setPresentationDirty(false);
            setForm(emptyForm);
            await loadEvents();
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Salvataggio non riuscito.');
        } finally {
            setSaving(false);
        }
    };

    const remove = async (event: Event) => {
        setDeletingId(event.id);
        setError('');
        setMessage('');
        try {
            await deleteEvent(event.id);
            if (editingId === event.id) resetEditor();
            setMessage('Evento eliminato.');
            await loadEvents();
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Eliminazione non riuscita.');
        } finally {
            setDeletingId(null);
            setEventToDelete(null);
        }
    };

    return (
        <section className="workspace-page">
            <div className="page-heading">
                <div>
                    <span className="eyebrow">Amministrazione / Eventi</span>
                    <h1>Programma e pubblica<span className="accent-text">.</span></h1>
                    <p>{canCreate ? 'Crea gli eventi, aggiorna le informazioni e controlla il loro stato. Apri la pagina PassHalo oppure prepara il modulo di prenotazione da inserire nel sito della band.' : 'Aggiorna gli eventi per cui hai il ruolo di amministratore.'}</p>
                </div>
                <button className="button" onClick={() => void loadEvents()} disabled={loading}>
                    <RefreshCw size={16} className={loading ? 'spinning' : ''} /> Aggiorna
                </button>
            </div>

            {error && <div className="notice error" role="alert">{error}</div>}
            {message && <div className="notice success" role="status">{message}</div>}

            <div className={`management-grid ${!canCreate && editingId === null ? 'collaborator-event-grid' : ''}`}>
                {(canCreate || editingId !== null) && <article className="panel editor-panel" id="event-editor">
                    <div className="panel-heading">
                        <div>
                            <span className="eyebrow">{editingId === null ? 'Nuovo evento' : `Modifica evento #${editingId}`}</span>
                            <h2>{editingId === null ? 'Dettagli dell’evento' : 'Aggiorna i dettagli'}</h2>
                        </div>
                        {editingId !== null && (
                            <button className="icon-button" type="button" onClick={resetEditor} aria-label="Annulla modifica">
                                <X size={18} />
                            </button>
                        )}
                    </div>

                    <form className="management-form" onSubmit={submit}>
                        <label>Nome evento<input value={form.name} onChange={(e) => updateField('name', e.target.value)} required /></label>
                        <label>Descrizione pubblica<textarea value={form.description} onChange={(e) => updateField('description', e.target.value)} rows={6} maxLength={4000} required /></label>
                        <label>Luogo<input value={form.location} onChange={(e) => updateField('location', e.target.value)} required /></label>
                        <div className="field-row">
                            <label>Inizio<input type="datetime-local" value={form.start} onChange={(e) => updateField('start', e.target.value)} required /></label>
                            <label>Fine<input type="datetime-local" value={form.end} onChange={(e) => updateField('end', e.target.value)} required /></label>
                        </div>
                        <fieldset className="event-presentation-editor">
                            <legend><Film size={15} /> Pagina pubblica</legend>
                            <p>Configura i contenuti visuali della landing di prenotazione. L’immagine viene usata anche come copertina e fallback del video.</p>
                            <label>URL immagine<input type="url" value={form.imageUrl} onChange={(e) => updateField('imageUrl', e.target.value)} placeholder="https://…/copertina.jpg" maxLength={2048} required /></label>
                            <label>URL video di sfondo <span className="optional-label">Facoltativo</span><input type="url" value={form.videoUrl} onChange={(e) => { setPresentationDirty(true); updateField('videoUrl', e.target.value); }} placeholder="MP4, WebM, YouTube o Vimeo" maxLength={2048} /></label>
                            <p className="field-help">Per il risultato migliore usa un video orizzontale, breve, senza audio e ottimizzato per il web.</p>
                        </fieldset>
                        <div className="field-row three-fields">
                            <label>Capienza<input type="number" min="1" step="1" value={form.totalTickets} onChange={(e) => updateField('totalTickets', e.target.value)} required /></label>
                            <label>Prezzo in cassa<input type="number" min="0.01" step="0.01" value={form.normalPrice} onChange={(e) => updateField('normalPrice', e.target.value)} required /></label>
                            <label>Prezzo prenotato<input type="number" min="0.01" step="0.01" value={form.bookingPrice} onChange={(e) => updateField('bookingPrice', e.target.value)} required /></label>
                        </div>
                        <div className="event-faq-editor">
                            <div className="event-faq-heading">
                                <div><span className="eyebrow"><CircleHelp size={14} /> Contenuti informativi</span><h3>FAQ dell’evento</h3></div>
                                <span>{form.faqs.length}/12</span>
                            </div>
                            <p>Se non aggiungi domande, la pagina mostrerà le FAQ standard generate dai dati dell’evento.</p>
                            {form.faqs.map((faq, index) => (
                                <article className="event-faq-item" key={index}>
                                    <div className="event-faq-item-heading"><strong>Domanda {index + 1}</strong><button className="icon-button danger" type="button" onClick={() => removeFaq(index)} aria-label={`Rimuovi domanda ${index + 1}`}><Trash2 size={15} /></button></div>
                                    <label>Domanda<input value={faq.question} onChange={(event) => updateFaq(index, 'question', event.target.value)} maxLength={160} required /></label>
                                    <label>Risposta<textarea value={faq.answer} onChange={(event) => updateFaq(index, 'answer', event.target.value)} rows={4} maxLength={1200} required /></label>
                                </article>
                            ))}
                            <button className="button full" type="button" onClick={addFaq} disabled={form.faqs.length >= 12}><Plus size={16} /> Aggiungi una FAQ</button>
                        </div>
                        <button className="button primary full" disabled={saving || loadingEditor}>
                            {editingId === null ? <Plus size={17} /> : <Edit3 size={17} />}
                            {saving ? 'Salvataggio…' : editingId === null ? 'Crea evento' : 'Salva modifiche'}
                        </button>
                    </form>
                </article>}

                <div className="resource-list" aria-busy={loading}>
                    {loading && <div className="panel empty-state"><h2>Caricamento eventi…</h2></div>}
                    {!loading && events.length === 0 && (
                        <div className="panel empty-state">
                            <CalendarDays size={28} />
                            <h2>Nessun evento</h2>
                            <p>{canCreate ? 'Compila il modulo per pubblicare il primo evento.' : 'Non hai eventi da amministrare.'}</p>
                        </div>
                    )}
                    {!loading && events.map((event) => (
                        <article className="panel event-card" key={event.id}>
                            <div className="event-card-image" style={{ backgroundImage: `url(${event.imageUrl})` }} aria-hidden="true" />
                            <div className="event-card-body">
                                <div className="resource-title">
                                    <div>
                                        <span className={`state-badge state-${event.eventState.toLowerCase()}`}>{stateLabels[event.eventState]}</span>
                                        <h2>{event.name}</h2>
                                        <span className="role-badge role-admin">{event.owner ? 'Proprietario' : 'Amministratore evento'}</span>
                                    </div>
                                    <span className="resource-id">#{event.id}</span>
                                </div>
                                <p className="event-description">{event.description}</p>
                                {(event.videoUrl || (event.faqs?.length ?? 0) > 0) && (
                                    <div className="event-content-badges">
                                        {event.videoUrl && <span><Film size={13} /> Video</span>}
                                        {(event.faqs?.length ?? 0) > 0 && <span><CircleHelp size={13} /> {event.faqs?.length} FAQ</span>}
                                    </div>
                                )}
                                <div className="event-facts">
                                    <span><CalendarDays size={14} />{formatDate(event.startDateTime)}</span>
                                    <span><MapPin size={14} />{event.location}</span>
                                </div>
                                <div className="event-card-footer">
                                    <span>{event.totalTickets.toLocaleString('it-IT')} posti · {event.bookingPrice.toLocaleString('it-IT', { style: 'currency', currency: 'EUR' })}</span>
                                    <div>
                                        <a className="button" href={`/prenota?eventId=${event.id}`} target="_blank" rel="noreferrer"><ArrowUpRight size={15} /> Pagina</a>
                                        <button className="button" onClick={() => void copyEmbedCode(event)}>{copiedEmbedId === event.id ? <Check size={15} /> : <Copy size={15} />}{copiedEmbedId === event.id ? 'Codice copiato' : 'Incorpora modulo'}</button>
                                        <Link className="button" to={`/admin/events/${event.id}/team`}><UsersRound size={15} /> Collaboratori</Link>
                                        <button className="button" disabled={loadingEditor || event.eventState === 'FINISHED'} onClick={() => void edit(event.id)}><Edit3 size={15} /> Modifica</button>
                                        <button className="button danger" disabled={deletingId === event.id || event.eventState === 'FINISHED'} onClick={() => setEventToDelete(event)}><Trash2 size={15} /> Elimina</button>
                                    </div>
                                </div>
                            </div>
                        </article>
                    ))}
                </div>
            </div>
            <ConfirmDialog
                open={eventToDelete !== null}
                title={`Eliminare “${eventToDelete?.name ?? 'questo evento'}”?`}
                description="L’evento e i dati collegati non saranno più disponibili. Questa azione non può essere annullata."
                confirmLabel="Elimina evento"
                busy={deletingId !== null}
                onCancel={() => setEventToDelete(null)}
                onConfirm={() => eventToDelete ? remove(eventToDelete) : undefined}
            />
        </section>
    );
}
