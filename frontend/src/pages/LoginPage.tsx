import { useState, type FormEvent } from 'react';
import { ArrowRight, LockKeyhole } from 'lucide-react';
import { Link, Navigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

export function LoginPage() {
    const { login, user, isLoading, authError } = useAuth();
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);

    if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin/dashboard' : '/staff/scan'} replace />;

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        const data = new FormData(event.currentTarget);
        setBusy(true);
        setError('');
        try {
            await login({
                email: String(data.get('email')).trim(),
                password: String(data.get('password')),
            });
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Accesso non riuscito. Riprova.');
        } finally {
            setBusy(false);
        }
    };

    return (
        <section className="form-page login-page">
            <div className="form-intro">
                <span className="eyebrow"><LockKeyhole size={14} /> Area riservata</span>
                <h1>Bentornato<span className="accent-text">.</span></h1>
                <p>Accedi con il tuo account per gestire gli eventi.</p>
            </div>
            <div className="panel form-panel">
                <form onSubmit={submit}>
                    {authError && <div className="notice error" role="alert">{authError}</div>}
                    {error && <div className="notice error" role="alert">{error}</div>}
                    <label>Email<input name="email" type="email" autoComplete="username" placeholder="nome@organizzazione.it" required /></label>
                    <label>Password<input name="password" type="password" autoComplete="current-password" placeholder="La tua password" required /></label>
                    <div className="form-utility"><Link className="text-link" to="/forgot-password">Password dimenticata?</Link></div>
                    <button className="button primary full" disabled={busy || isLoading}>{busy ? 'Accesso in corso…' : 'Accedi'}<ArrowRight size={17} /></button>
                </form>
                <p className="form-note auth-switch">Nuovo organizzatore? <Link className="text-link" to="/register">Crea un account ADMIN</Link></p>
                <p className="form-note auth-switch">Fai parte dello staff? <Link className="text-link" to="/staff/access">Entra con il codice dell’evento</Link></p>
            </div>
        </section>
    );
}
