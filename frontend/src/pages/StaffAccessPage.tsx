import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { CheckCircle2, Clock3, Keyboard, LogOut, Minus, Plus, QrCode, Wifi, WifiOff, XCircle } from 'lucide-react';
import { Link } from 'react-router-dom';
import { getStaffAccessStatus, requestStaffAccess, staffAddWalkIn, staffCheckIn, staffLogout, staffRemoveWalkIn, type StaffAccessStatus } from '../api/staffAccess';
import { isApiError } from '../api/client';
import { QrCameraScanner } from '../components/QrCameraScanner';
import { PaymentMethodSelector } from '../components/PaymentMethodSelector';
import { paymentMethodLabel } from '../utils/paymentMethod';
import type { PaymentMethod } from '../types';

const uuidPattern = /[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i;

interface CheckResult {
    ok: boolean;
    message: string;
}

export function StaffAccessPage() {
    const [status, setStatus] = useState<StaffAccessStatus | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [code, setCode] = useState('');
    const [pass, setPass] = useState('');
    const [error, setError] = useState('');
    const [result, setResult] = useState<CheckResult | null>(null);
    const [walkInCounts, setWalkInCounts] = useState<Record<PaymentMethod, number>>({ CASH: 0, CARD: 0 });
    const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | null>(null);
    const [walkInMethod, setWalkInMethod] = useState<PaymentMethod | null>(null);
    const [walkInMessage, setWalkInMessage] = useState('');
    const [online, setOnline] = useState(() => navigator.onLine);
    const checkingRef = useRef(false);

    const refreshStatus = useCallback(async () => {
        if (checkingRef.current) return;
        checkingRef.current = true;
        try {
            const current = await getStaffAccessStatus();
            setStatus(current);
            setError('');
        } catch (requestError) {
            if (isApiError(requestError) && requestError.status === 401) {
                setStatus(null);
            } else {
                setError(requestError instanceof Error ? requestError.message : 'Stato della richiesta non disponibile.');
            }
        } finally {
            checkingRef.current = false;
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        const timer = window.setTimeout(() => void refreshStatus(), 0);
        return () => window.clearTimeout(timer);
    }, [refreshStatus]);

    useEffect(() => {
        if (status?.state !== 'PENDING' && status?.state !== 'APPROVED') return;
        const timer = window.setInterval(() => void refreshStatus(), 5_000);
        return () => window.clearInterval(timer);
    }, [refreshStatus, status?.state]);

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

    const submitCode = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (!code.trim() || busy) return;
        setBusy(true);
        setError('');
        setResult(null);
        try {
            const next = await requestStaffAccess(code.trim());
            setCode('');
            setStatus(next);
            setWalkInCounts({ CASH: 0, CARD: 0 });
            setPaymentMethod(null);
            setWalkInMethod(null);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Non è stato possibile inviare la richiesta.');
        } finally { setBusy(false); }
    };

    const leave = async () => {
        setBusy(true);
        setError('');
        try {
            await staffLogout();
            setStatus(null);
            setPass('');
            setResult(null);
            setWalkInCounts({ CASH: 0, CARD: 0 });
            setPaymentMethod(null);
            setWalkInMethod(null);
            setWalkInMessage('');
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Uscita non riuscita.');
        } finally { setBusy(false); }
    };

    const checkPass = useCallback(async (value: string) => {
        if (busy || !online || status?.state !== 'APPROVED' || paymentMethod === null) return;
        const uuid = value.match(uuidPattern)?.[0];
        if (!uuid) {
            setResult({ ok: false, message: 'Il QR non contiene un pass valido.' });
            return;
        }
        setBusy(true);
        setResult(null);
        try {
            const response = await staffCheckIn(uuid, paymentMethod);
            setResult({ ok: true, message: `Pass convalidato · ${response.eventName} · ${paymentMethodLabel(paymentMethod)}` });
            setPaymentMethod(null);
            setPass('');
            navigator.vibrate?.(100);
        } catch (requestError) {
            setResult({ ok: false, message: requestError instanceof Error ? requestError.message : 'Pass non valido o già utilizzato.' });
            navigator.vibrate?.([80, 60, 80]);
            if (isApiError(requestError) && (requestError.status === 401 || requestError.status === 403)) {
                void refreshStatus();
            }
        } finally { setBusy(false); }
    }, [busy, online, refreshStatus, status?.state, paymentMethod]);

    const adjustWalkIn = async (direction: 'add' | 'remove') => {
        if (busy || !online || status?.state !== 'APPROVED' || walkInMethod === null) return;
        if (direction === 'remove' && walkInCounts[walkInMethod] <= 0) return;
        setBusy(true);
        setWalkInMessage('');
        setError('');
        try {
            if (direction === 'add') await staffAddWalkIn(walkInMethod);
            else await staffRemoveWalkIn(walkInMethod);
            setWalkInCounts((counts) => ({ ...counts, [walkInMethod]: counts[walkInMethod] + (direction === 'add' ? 1 : -1) }));
            setWalkInMessage(`${direction === 'add' ? 'Ingresso registrato' : 'Ingresso rimosso'} · ${paymentMethodLabel(walkInMethod)}.`);
            setWalkInMethod(null);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Conteggio non aggiornato.');
            if (isApiError(requestError) && (requestError.status === 401 || requestError.status === 403)) {
                void refreshStatus();
            }
        } finally { setBusy(false); }
    };

    if (loading) return <section className="form-page"><div className="panel form-panel" role="status">Controllo della sessione staff…</div></section>;

    if (status?.state === 'APPROVED') return (
        <section className="scan-page">
            <div className="scan-heading"><div><span className="eyebrow"><QrCode size={15} /> Staff · {status.eventName}</span><h1>Controllo ingressi<span className="accent-text">.</span></h1><p>Puoi verificare i pass e registrare gli ingressi senza prenotazione per questo evento.</p></div><span className={`connection-status ${online ? 'online' : 'offline'}`} role="status">{online ? <Wifi size={15} /> : <WifiOff size={15} />}{online ? 'Connesso' : 'Senza connessione'}</span></div>
            {!online && <div className="notice error" role="alert">Serve una connessione per verificare il QR.</div>}
            {error && <div className="notice error" role="alert">{error}</div>}
            <div className="scan-grid">
                <article className="panel scan-panel">
                    {result && <div className={`scan-result ${result.ok ? 'success' : 'error'}`} role={result.ok ? 'status' : 'alert'}>{result.ok ? <CheckCircle2 size={34} /> : <XCircle size={34} />}<div><strong>{result.ok ? 'INGRESSO OK' : 'INGRESSO KO'}</strong><span>{result.message}</span></div></div>}
                    <PaymentMethodSelector value={paymentMethod} onChange={setPaymentMethod} disabled={busy || !online} label="Pagamento del pass da convalidare" />
                    <QrCameraScanner disabled={busy || !online || paymentMethod === null} onDetected={checkPass} />
                    <div className="scan-divider"><span>oppure</span></div>
                    <form onSubmit={(event) => { event.preventDefault(); void checkPass(pass); }}><label htmlFor="staff-pass"><Keyboard size={15} /> Codice del pass</label><div className="scan-input-row"><input id="staff-pass" value={pass} onChange={(event) => setPass(event.target.value)} placeholder="UUID del biglietto" autoComplete="off" required /><button className="button primary" disabled={busy || !online || !pass.trim() || paymentMethod === null}>{busy ? 'Verifica…' : 'Convalida'}</button></div></form>
                </article>
                <aside className="panel scan-history">
                    <span className="eyebrow">Evento assegnato</span><h2>{status.eventName}</h2><p>La verifica mostra soltanto l’esito. Nessun dato personale del cliente è visibile allo staff.</p>
                    <div className="walk-in-tools">
                        <span className="eyebrow">Ingressi senza prenotazione</span><h2>Registra un accesso</h2>
                        <p>Registrati da questo dispositivo: <strong>{walkInCounts.CASH} contanti · {walkInCounts.CARD} carta</strong></p>
                        <PaymentMethodSelector value={walkInMethod} onChange={setWalkInMethod} disabled={busy || !online} label="Pagamento dell’ingresso senza prenotazione" />
                        <div className="counter-actions">
                            <button className="button" disabled={busy || !online || walkInMethod === null || walkInCounts[walkInMethod] <= 0} onClick={() => void adjustWalkIn('remove')}><Minus size={16} /> Correggi metodo selezionato</button>
                            <button className="button primary" disabled={busy || !online || walkInMethod === null} onClick={() => void adjustWalkIn('add')}><Plus size={16} /> Aggiungi ingresso</button>
                        </div>{walkInMessage && <p className="notice success" role="status">{walkInMessage}</p>}
                    </div>
                    <button className="button staff-leave" disabled={busy} onClick={() => void leave()}><LogOut size={16} /> Termina la sessione</button>
                </aside>
            </div>
        </section>
    );

    return (
        <section className="form-page staff-access-page">
            <div className="form-intro"><span className="eyebrow"><QrCode size={14} /> Ingresso staff</span><h1>Accedi con il codice<span className="accent-text">.</span></h1><p>Non serve registrarsi. Chiedi il codice all’organizzatore e attendi la sua approvazione.</p></div>
            <div className="panel form-panel">
                {error && <div className="notice error" role="alert">{error}</div>}
                {status?.state === 'PENDING' && <div className="staff-waiting" role="status"><Clock3 size={26} /><h2>Richiesta inviata</h2><p>L’organizzatore deve approvare l’accesso per <strong>{status.eventName}</strong>. Questa pagina si aggiorna da sola.</p><button className="button" disabled={busy} onClick={() => void refreshStatus()}>Controlla ora</button><button className="button" disabled={busy} onClick={() => void leave()}>Annulla richiesta</button></div>}
                {status?.state === 'REJECTED' && <div className="notice error" role="alert">La richiesta per {status.eventName} è stata rifiutata. Chiedi all’organizzatore un nuovo codice, se necessario.</div>}
                {status?.state === 'EXPIRED' && <div className="notice error" role="alert">L’accesso per {status.eventName} è terminato o il codice è scaduto.</div>}
                {status?.state !== 'PENDING' && <form onSubmit={submitCode}><label htmlFor="staff-event-code">Codice dell’evento<input id="staff-event-code" value={code} onChange={(event) => setCode(event.target.value)} placeholder="XXXX-XXXX-XXXX-XXXX" autoComplete="off" autoCapitalize="characters" required /></label><button className="button primary full" disabled={busy || !online || !code.trim()}>{busy ? 'Invio…' : 'Richiedi accesso'}</button></form>}
                <p className="form-note auth-switch">Sei un organizzatore? <Link className="text-link" to="/login">Accedi al tuo account</Link></p>
            </div>
        </section>
    );
}
