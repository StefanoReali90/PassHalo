import { NavLink } from 'react-router-dom';
import { KeyRound, Mail } from 'lucide-react';
import { useAuth } from '../context/useAuth';

export function SettingsNavigation() {
    const { user } = useAuth();
    return (
        <nav className="settings-navigation" aria-label="Impostazioni">
            <NavLink className="button secondary" to="/account" end><KeyRound size={16} />Sicurezza account</NavLink>
            {user?.role === 'ADMIN' && <NavLink className="button secondary" to="/settings/mail"><Mail size={16} />Configurazione mail</NavLink>}
        </nav>
    );
}
