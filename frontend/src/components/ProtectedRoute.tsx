import { useEffect, useState, type ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { RefreshCw, WifiOff } from 'lucide-react';
import { getMyEvents } from '../api/events';
import { useAuth } from '../context/useAuth';
import type { UserRole } from '../types';

interface ProtectedRouteProps {
    children: ReactNode;
    roles?: UserRole[];
    eventAdmin?: boolean;
}

export function ProtectedRoute({ children, roles, eventAdmin = false }: ProtectedRouteProps) {
    const { user, isLoading, authError, retrySession } = useAuth();
    const [eventAdminAccess, setEventAdminAccess] = useState<{ email: string; allowed: boolean; error: boolean } | null>(null);
    const [retryAccess, setRetryAccess] = useState(0);

    useEffect(() => {
        if (!eventAdmin || !user || user.role === 'ADMIN') return;
        let active = true;
        getMyEvents()
            .then((events) => { if (active) setEventAdminAccess({ email: user.email, allowed: events.some((event) => event.role === 'EVENT_ADMIN'), error: false }); })
            .catch(() => { if (active) setEventAdminAccess({ email: user.email, allowed: false, error: true }); });
        return () => { active = false; };
    }, [eventAdmin, user, retryAccess]);

    if (isLoading) {
        return <div className="route-loader" role="status"><span className="spinner" />Caricamento sessione…</div>;
    }
    if (authError) {
        return (
            <div className="service-unavailable" role="alert">
                <WifiOff size={30} />
                <h1>Server non disponibile</h1>
                <p>{authError}</p>
                <button className="button primary" onClick={() => void retrySession()}><RefreshCw size={16} /> Riprova</button>
            </div>
        );
    }
    if (!user) return <Navigate to="/login" replace />;
    if (roles && !roles.includes(user.role)) {
        return <Navigate to={user.role === 'ADMIN' ? '/admin/dashboard' : '/staff/scan'} replace />;
    }
    const access = eventAdminAccess?.email === user.email ? eventAdminAccess : null;
    if (eventAdmin && user.role !== 'ADMIN' && access === null) {
        return <div className="route-loader" role="status"><span className="spinner" />Verifica accesso all’evento…</div>;
    }
    if (eventAdmin && user.role !== 'ADMIN' && access?.error) {
        return <div className="service-unavailable" role="alert"><WifiOff size={30} /><h1>Verifica non disponibile</h1><p>Non riusciamo a controllare i tuoi eventi assegnati.</p><button className="button primary" onClick={() => { setEventAdminAccess(null); setRetryAccess((current) => current + 1); }}><RefreshCw size={16} /> Riprova</button></div>;
    }
    if (eventAdmin && user.role !== 'ADMIN' && !access?.allowed) return <Navigate to="/collaborations" replace />;
    return <>{children}</>;
}
