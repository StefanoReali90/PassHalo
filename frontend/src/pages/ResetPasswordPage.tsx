import { useEffect, useState, type FormEvent } from 'react';
import { ArrowLeft, KeyRound } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { resetPassword } from '../api/auth';

export function ResetPasswordPage() {
    const [searchParams] = useSearchParams();
    const [token, setToken] = useState(() =>
        new URLSearchParams(window.location.hash.slice(1)).get('token') ?? searchParams.get('token') ?? '');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [complete, setComplete] = useState(false);

    useEffect(() => {
        const url = new URL(window.location.href);
        if (!url.hash && !url.searchParams.has('token')) return;
        url.hash = '';
        url.searchParams.delete('token');
        window.history.replaceState(null, '', `${url.pathname}${url.search}`);
    }, []);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        const newPassword = String(data.get('newPassword'));
        const confirmationPassword = String(data.get('confirmationPassword'));
        if (newPassword !== confirmationPassword) {
            setError('La nuova password e la conferma non coincidono.');
            return;
        }

        setBusy(true);
        setError('');
        try {
            await resetPassword({ token: token.trim(), newPassword, confirmationPassword });
            setComplete(true);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Reimpostazione non riuscita.');
        } finally {
            setBusy(false);
        }
    };

    return (
        <section className="form-page login-page">
            <div className="form-intro">
                <span className="eyebrow"><KeyRound size={14} /> Nuova password</span>
                <h1>Proteggi l’account<span className="accent-text">.</span></h1>
                <p>Usa il token di recupero e scegli una nuova password.</p>
            </div>
            <div className="panel form-panel">
                {error && <div className="notice error" role="alert">{error}</div>}
                {complete ? (
                    <div>
                        <div className="notice success" role="status">Password aggiornata. Ora puoi accedere con le nuove credenziali.</div>
                        <Link className="button primary full" to="/login">Vai al login</Link>
                    </div>
                ) : (
                    <form onSubmit={submit}>
                        <label>Token di recupero<input value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" required /></label>
                        <label>Nuova password<input name="newPassword" type="password" autoComplete="new-password" minLength={8} maxLength={100} required /></label>
                        <label>Conferma password<input name="confirmationPassword" type="password" autoComplete="new-password" minLength={8} maxLength={100} required /></label>
                        <button className="button primary full" disabled={busy || !token.trim()}><KeyRound size={16} />{busy ? 'Aggiornamento…' : 'Imposta nuova password'}</button>
                    </form>
                )}
                <Link className="text-link form-back-link" to="/login"><ArrowLeft size={14} /> Torna al login</Link>
            </div>
        </section>
    );
}
