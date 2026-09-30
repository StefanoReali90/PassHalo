import { useEffect, useState, type FormEvent } from 'react';
import { Mail } from 'lucide-react';
import { connectBrevo, disconnectBrevo, getBrevoStatus, rotateBrevoKey, type BrevoStatus } from '../api/brevo';
import { deleteSmtpSettings, getSmtpSettings, saveSmtpSettings, testSmtpSettings, type SmtpSettings, type SmtpSettingsInput } from '../api/smtp';
import { SettingsNavigation } from '../components/SettingsNavigation';
import { useAuth } from '../context/useAuth';

export function MailSettingsPage() {
    const { user } = useAuth();
    const [brevoStatus, setBrevoStatus] = useState<BrevoStatus | null>(null);
    const [brevoBusy, setBrevoBusy] = useState(false);
    const [brevoError, setBrevoError] = useState('');
    const [brevoMessage, setBrevoMessage] = useState('');
    const [smtpStatus, setSmtpStatus] = useState<SmtpSettings | null>(null);
    const [smtpBusy, setSmtpBusy] = useState(false);
    const [smtpError, setSmtpError] = useState('');
    const [smtpMessage, setSmtpMessage] = useState('');
    const [smtp, setSmtp] = useState<SmtpSettingsInput>({ host: 'smtp-relay.brevo.com', port: 587,
        encryption: 'STARTTLS', username: '', password: '', fromEmail: '', fromName: '' });

    useEffect(() => {
        if (user?.role !== 'ADMIN') return;
        getSmtpSettings().then(status => {
            setSmtpStatus(status);
            if (status.configured) setSmtp({ host: status.host || '', port: status.port || 587,
                encryption: status.encryption || 'STARTTLS', username: status.username || '',
                password: '', fromEmail: status.fromEmail || '', fromName: status.fromName || '' });
        }).catch(error => setSmtpError(error instanceof Error ? error.message : 'Impostazioni SMTP non disponibili.'));
    }, [user?.role]);

    const submitSmtp = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setSmtpBusy(true); setSmtpError(''); setSmtpMessage('');
        try {
            const status = await saveSmtpSettings(smtp);
            setSmtpStatus(status);
            setSmtp(current => ({ ...current, password: '' }));
            setSmtpMessage('Impostazioni salvate. Le prossime email dei tuoi eventi useranno questo mittente.');
        } catch (error) {
            setSmtpError(error instanceof Error ? error.message : 'Salvataggio SMTP non riuscito.');
        } finally { setSmtpBusy(false); }
    };

    const removeSmtp = async () => {
        if (!window.confirm('Rimuovere le impostazioni SMTP? Le email dei tuoi eventi useranno il mittente predefinito di PassHalo.')) return;
        setSmtpBusy(true); setSmtpError(''); setSmtpMessage('');
        try {
            await deleteSmtpSettings();
            setSmtpStatus({ configured: false, host: null, port: null, encryption: null,
                username: null, fromEmail: null, fromName: null });
            setSmtp(current => ({ ...current, password: '' }));
            setSmtpMessage('Impostazioni SMTP rimosse.');
        } catch (error) {
            setSmtpError(error instanceof Error ? error.message : 'Rimozione SMTP non riuscita.');
        } finally { setSmtpBusy(false); }
    };

    const testSmtp = async () => {
        setSmtpBusy(true); setSmtpError(''); setSmtpMessage('');
        try {
            await testSmtpSettings();
            setSmtpMessage(`Email di prova inviata a ${user?.email}.`);
        } catch (error) {
            setSmtpError(error instanceof Error ? error.message : 'Invio di prova non riuscito.');
        } finally { setSmtpBusy(false); }
    };

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

    return (
        <section className="workspace-page account-page">
            <div className="page-heading">
                <div>
                    <span className="eyebrow">Impostazioni / Email</span>
                    <h1>Configurazione mail<span className="accent-text">.</span></h1>
                    <p>Configura il mittente delle email dei tuoi eventi e il collegamento marketing.</p>
                </div>
            </div>

            <SettingsNavigation />
            <div className="account-grid">
                {user?.role === 'ADMIN' && (
                    <article className="panel editor-panel">
                        <div className="panel-heading"><div><span className="eyebrow">Email eventi</span><h2>Mittente e server SMTP</h2></div><Mail size={21} /></div>
                        <p>Le conferme con QR e gli inviti dei tuoi eventi partiranno dal tuo server email. Senza configurazione continuerà a essere usato il mittente di PassHalo.</p>
                        <p>Per Brevo usa il login SMTP e una chiave SMTP, diversi dalla chiave API della sezione marketing. Il mittente deve essere verificato nel provider.</p>
                        {smtpError && <div className="notice error" role="alert">{smtpError}</div>}
                        {smtpMessage && <div className="notice success" role="status">{smtpMessage}</div>}
                        {smtpStatus?.configured && <div className="notice success" role="status">SMTP configurato: {smtpStatus.fromName} &lt;{smtpStatus.fromEmail}&gt;</div>}
                        {smtpStatus && <form className="management-form" onSubmit={submitSmtp}>
                            <label>Provider
                                <select value={smtp.host === 'smtp-relay.brevo.com' ? 'brevo' : 'other'}
                                    onChange={event => setSmtp(current => event.target.value === 'brevo'
                                        ? { ...current, host: 'smtp-relay.brevo.com', port: 587, encryption: 'STARTTLS' }
                                        : { ...current, host: '', port: 587, encryption: 'STARTTLS' })}>
                                    <option value="brevo">Brevo</option><option value="other">Altro provider SMTP</option>
                                </select>
                            </label>
                            <label>Host SMTP<input value={smtp.host} onChange={event => setSmtp(current => ({ ...current, host: event.target.value }))}
                                autoComplete="off" maxLength={253} placeholder="smtp.example.com" required /></label>
                            <label>Porta<input type="number" min="1" max="65535" value={smtp.port}
                                onChange={event => setSmtp(current => ({ ...current, port: Number(event.target.value) }))} required /></label>
                            <label>Cifratura<select value={smtp.encryption} onChange={event => setSmtp(current => ({ ...current, encryption: event.target.value as 'STARTTLS' | 'SSL' }))}>
                                <option value="STARTTLS">STARTTLS</option><option value="SSL">SSL/TLS</option></select></label>
                            <label>Login SMTP<input value={smtp.username} onChange={event => setSmtp(current => ({ ...current, username: event.target.value }))}
                                autoComplete="off" maxLength={320} required /></label>
                            <label>{smtpStatus.configured ? 'Nuova password SMTP (lascia vuoto per conservarla)' : 'Password o chiave SMTP'}
                                <input type="password" value={smtp.password} onChange={event => setSmtp(current => ({ ...current, password: event.target.value }))}
                                    autoComplete="new-password" maxLength={512} required={!smtpStatus.configured} /></label>
                            <label>Nome mittente<input value={smtp.fromName} onChange={event => setSmtp(current => ({ ...current, fromName: event.target.value }))}
                                maxLength={100} placeholder="La tua organizzazione" required /></label>
                            <label>Email mittente<input type="email" value={smtp.fromEmail} onChange={event => setSmtp(current => ({ ...current, fromEmail: event.target.value }))}
                                maxLength={320} placeholder="booking@tuodominio.it" required /></label>
                            <button className="button primary full" disabled={smtpBusy}>{smtpBusy ? 'Salvataggio…' : 'Salva impostazioni email'}</button>
                        </form>}
                        {smtpStatus?.configured && <>
                            <button className="button secondary" type="button" disabled={smtpBusy} onClick={testSmtp}>Invia email di prova</button>
                            <button className="button secondary" type="button" disabled={smtpBusy} onClick={removeSmtp}>Rimuovi impostazioni SMTP</button>
                        </>}
                    </article>
                )}
                {user?.role === 'ADMIN' && (
                    <article className="panel editor-panel">
                        <div className="panel-heading"><div><span className="eyebrow">Marketing</span><h2>Account Brevo marketing</h2></div><Mail size={21} /></div>
                        <p>Collega anche un account Brevo dedicato o gestito da un collaboratore, con la sua chiave API e lista. Solo i contatti che hanno dato il consenso per i tuoi eventi verranno sincronizzati.</p>
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
