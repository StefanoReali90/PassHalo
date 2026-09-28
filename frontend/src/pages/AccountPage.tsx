import { useEffect, useState, type FormEvent } from 'react';
import { KeyRound, ShieldCheck, Mail } from 'lucide-react';
import { changePassword } from '../api/auth';
import { connectBrevo, disconnectBrevo, getBrevoStatus, rotateBrevoKey, type BrevoStatus } from '../api/brevo';
import { useAuth } from '../context/useAuth';

export function AccountPage() {
    const { user } = useAuth();
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [brevoStatus, setBrevoStatus] = useState<BrevoStatus | null>(null);
    const [brevoBusy, setBrevoBusy] = useState(false);
    const [brevoError, setBrevoError] = useState('');
    const [brevoMessage, setBrevoMessage] = useState('');

    useEffect(() => {
        if (user?.role !== 'ADMIN') return;
        let active = true;
        const refresh = () => {
            getBrevoStatus().then(status => { if (active) setBrevoStatus(status); })
                .catch(error => { if (active) setBrevoError(error instanceof Error ? error.message : 'Stato Brevo non disponibile.'); });
        };
        refresh();
        const interval = window.setInterval(refresh, 15_000);
        return () => { active = false; window.clearInterval(interval); };
    }, [user?.role]);

    const submitBrevo = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const form = event.currentTarget;
        const data = new FormData(form);
        setBrevoBusy(true);
        setBrevoError('');
        setBrevoMessage('');
        try {
            const status = await connectBrevo(String(data.get('apiKey')), Number(data.get('listId')));
            setBrevoStatus(status);
            form.reset();
            setBrevoMessage('Account Brevo collegato. I contatti saranno sincronizzati.');
        } catch (error) {
            setBrevoError(error instanceof Error ? error.message : 'Collegamento Brevo non riuscito.');
        } finally {
            setBrevoBusy(false);
        }
    };

    const replaceBrevoKey = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const form = event.currentTarget;
        const data = new FormData(form);
        setBrevoBusy(true);
        setBrevoError('');
        setBrevoMessage('');
        try {
            const status = await rotateBrevoKey(String(data.get('apiKey')));
            setBrevoStatus(status);
            form.reset();
            setBrevoMessage('Chiave Brevo sostituita. La sincronizzazione riprenderà automaticamente.');
        } catch (error) {
            setBrevoError(error instanceof Error ? error.message : 'Impossibile sostituire la chiave Brevo.');
        } finally {
            setBrevoBusy(false);
        }
    };

    const removeBrevo = async () => {
        if (!window.confirm('Scollegare Brevo? I contatti attivi di PassHalo saranno rimossi dalla lista configurata.')) return;
        setBrevoBusy(true);
        setBrevoError('');
        setBrevoMessage('');
        try {
            await disconnectBrevo();
            setBrevoStatus({ connected: false, listId: null, pendingContacts: 0 });
        } catch (error) {
            setBrevoError(error instanceof Error ? error.message : 'Impossibile scollegare Brevo.');
        } finally {
            setBrevoBusy(false);
        }
    };

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const form = event.currentTarget;
        const data = new FormData(form);
        const newPassword = String(data.get('newPassword'));
        const confirmationPassword = String(data.get('confirmationPassword'));

        if (newPassword !== confirmationPassword) {
            setError('La nuova password e la conferma non coincidono.');
            return;
        }

        setBusy(true);
        setError('');
        setMessage('');
        try {
            await changePassword({
                oldPassword: String(data.get('oldPassword')),
                newPassword,
                confirmationPassword,
            });
            form.reset();
            setMessage('Password aggiornata con successo.');
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Cambio password non riuscito.');
        } finally {
            setBusy(false);
        }
    };

    return (
        <section className="workspace-page account-page">
            <div className="page-heading">
                <div>
                    <span className="eyebrow">Profilo / Sicurezza</span>
                    <h1>Il tuo account<span className="accent-text">.</span></h1>
                    <p>Controlla i dati della sessione e aggiorna la password.</p>
                </div>
            </div>

            <div className="account-grid">
                <article className="panel profile-summary">
                    <span className="avatar account-avatar">{user?.name[0]}{user?.surname[0]}</span>
                    <span className={`role-badge role-${user?.role.toLowerCase()}`}><ShieldCheck size={14} />{user?.role}</span>
                    <h2>{user?.name} {user?.surname}</h2>
                    <p>{user?.email}</p>
                </article>

                <article className="panel editor-panel">
                    <div className="panel-heading"><div><span className="eyebrow">Credenziali</span><h2>Cambia password</h2></div><KeyRound size={21} /></div>
                    {error && <div className="notice error" role="alert">{error}</div>}
                    {message && <div className="notice success" role="status">{message}</div>}
                    <form className="management-form" onSubmit={submit}>
                        <label>Password attuale<input name="oldPassword" type="password" autoComplete="current-password" required /></label>
                        <label>Nuova password<input name="newPassword" type="password" autoComplete="new-password" minLength={8} maxLength={100} required /></label>
                        <label>Conferma nuova password<input name="confirmationPassword" type="password" autoComplete="new-password" minLength={8} maxLength={100} required /></label>
                        <button className="button primary full" disabled={busy}><KeyRound size={16} />{busy ? 'Aggiornamento…' : 'Aggiorna password'}</button>
                    </form>
                </article>
                {user?.role === 'ADMIN' && (
                    <article className="panel editor-panel">
                        <div className="panel-heading"><div><span className="eyebrow">Marketing</span><h2>Il tuo account Brevo</h2></div><Mail size={21} /></div>
                        <p>Collega una lista dedicata ai tuoi eventi. Solo i contatti che hanno dato il consenso per i tuoi eventi verranno sincronizzati.</p>
                        {brevoError && <div className="notice error" role="alert">{brevoError}</div>}
                        {brevoMessage && <div className="notice success" role="status">{brevoMessage}</div>}
                        {brevoStatus?.connected ? (
                            <>
                                <div className="notice success" role="status">
                                    Brevo collegato alla lista {brevoStatus.listId}.
                                    {brevoStatus.pendingContacts > 0 && ` Contatti in attesa di sincronizzazione: ${brevoStatus.pendingContacts}.`}
                                </div>
                                <form className="management-form" onSubmit={replaceBrevoKey}>
                                    <p>Se la chiave è scaduta o è stata revocata, creane una nuova nello stesso account Brevo. La lista e i contatti in attesa restano associati al tuo account.</p>
                                    <label>Nuova chiave API Brevo<input name="apiKey" type="password" autoComplete="off" maxLength={512} required /></label>
                                    <button className="button secondary" disabled={brevoBusy}>{brevoBusy ? 'Aggiornamento…' : 'Sostituisci chiave API'}</button>
                                </form>
                                <button className="button secondary" type="button" onClick={removeBrevo}
                                    disabled={brevoBusy || brevoStatus.pendingContacts > 0}>
                                    {brevoBusy ? 'Scollegamento…' : 'Scollega Brevo'}
                                </button>
                            </>
                        ) : brevoStatus ? (
                            <form className="management-form" onSubmit={submitBrevo}>
                                <p>Crea una lista e una chiave API dedicata a PassHalo nel tuo account Brevo. La chiave dà accesso all'intero account: verrà custodita cifrata sul server e non sarà più mostrata qui.</p>
                                <label>ID della lista Brevo<input name="listId" type="number" min="1" step="1" required /></label>
                                <label>Chiave API Brevo<input name="apiKey" type="password" autoComplete="off" maxLength={512} required /></label>
                                <button className="button primary full" disabled={brevoBusy}>{brevoBusy ? 'Collegamento…' : 'Collega Brevo'}</button>
                            </form>
                        ) : null}
                    </article>
                )}
            </div>
        </section>
    );
}
