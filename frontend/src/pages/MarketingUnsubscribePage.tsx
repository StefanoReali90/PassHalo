import { useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../api/client';

export function MarketingUnsubscribePage() {
    const [token] = useState(() => new URLSearchParams(window.location.hash.slice(1)).get('token') ?? '');
    const [busy, setBusy] = useState(false);
    const [complete, setComplete] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        if (token) window.history.replaceState(null, '', window.location.pathname);
    }, [token]);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        setError('');
        try {
            await apiFetch<void>('/marketing/unsubscribe', {
                method: 'POST',
                body: JSON.stringify({ token }),
            });
            setComplete(true);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Richiesta non riuscita. Riprova.');
        } finally {
            setBusy(false);
        }
    }

    return (
        <section className="form-page login-page">
            <div className="form-intro">
                <span className="eyebrow">Preferenze privacy</span>
                <h1>Comunicazioni promozionali<span className="accent-text">.</span></h1>
                <p>Puoi revocare il consenso e chiedere la cancellazione dei dati usati per il marketing.</p>
            </div>
            <div className="panel form-panel">
                {error && <div className="notice error" role="alert">{error}</div>}
                {complete ? (
                    <div className="notice success" role="status">Consenso revocato. I dati marketing associati sono stati cancellati.</div>
                ) : token ? (
                    <form onSubmit={submit}>
                        <p>Conferma per revocare il consenso e cancellare i dati marketing collegati al tuo indirizzo email.</p>
                        <button className="button primary full" disabled={busy}>{busy ? 'Elaborazione…' : 'Revoca consenso e cancella i dati'}</button>
                    </form>
                ) : (
                    <div className="notice error" role="status">Il link non contiene un token valido. Usa il collegamento presente nell’email di conferma.</div>
                )}
                <Link className="text-link form-back-link" to="/privacy">Informazioni privacy</Link>
            </div>
        </section>
    );
}
