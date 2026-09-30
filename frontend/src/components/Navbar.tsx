import { useEffect, useRef, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Bell, CalendarDays, ChevronDown, KeyRound, LayoutDashboard, LogOut, Mail, Menu, Moon, ScanLine, Sun, TicketCheck, UsersRound, X } from 'lucide-react';
import { Brand } from './Brand';
import { EVENT_ACCESS_CHANGED, getMyEvents } from '../api/events';
import { getStaffRequests } from '../api/staffAccess';
import { useAuth } from '../context/useAuth';

export function Navbar() {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const location = useLocation();
    const [open, setOpen] = useState(false);
    const [navOpen, setNavOpen] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [theme, setTheme] = useState(() => localStorage.getItem('cp-theme') === 'light' ? 'light' : 'dark');
    const [accent, setAccent] = useState(() => localStorage.getItem('cp-accent') || 'lime');
    const [eventAdminAccess, setEventAdminAccess] = useState<{ email: string; allowed: boolean } | null>(null);
    const [staffNotices, setStaffNotices] = useState<{ email: string; events: Array<{ id: number; name: string; count: number }> } | null>(null);
    const [noticesOpen, setNoticesOpen] = useState(false);
    const menu = useRef<HTMLDivElement>(null);
    const noticesMenu = useRef<HTMLDivElement>(null);

    useEffect(() => {
        document.documentElement.dataset.theme = theme;
        document.documentElement.dataset.accent = accent;
        localStorage.setItem('cp-theme', theme);
        localStorage.setItem('cp-accent', accent);
    }, [theme, accent]);

    useEffect(() => {
        const close = (event: PointerEvent) => {
            if (!menu.current?.contains(event.target as Node)) setOpen(false);
            if (!noticesMenu.current?.contains(event.target as Node)) setNoticesOpen(false);
        };
        const escape = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                setOpen(false);
                setNavOpen(false);
                setNoticesOpen(false);
                menu.current?.querySelector('button')?.focus();
            }
        };
        document.addEventListener('pointerdown', close);
        document.addEventListener('keydown', escape);
        return () => {
            document.removeEventListener('pointerdown', close);
            document.removeEventListener('keydown', escape);
        };
    }, []);

    useEffect(() => {
        if (!user) return;
        let active = true;
        const refreshAccess = () => {
            void getMyEvents()
                .then(async (events) => {
                    if (!active) return;
                    setEventAdminAccess({ email: user.email, allowed: events.some((event) => event.role === 'EVENT_ADMIN') });
                    const owned = events.filter((event) => event.owner && event.eventState !== 'FINISHED');
                    const results = await Promise.allSettled(owned.map((event) => getStaffRequests(event.id)));
                    if (!active) return;
                    setStaffNotices({ email: user.email, events: owned.flatMap((event, index) => {
                        const result = results[index];
                        return result.status === 'fulfilled' && result.value.length > 0
                            ? [{ id: event.id, name: event.name, count: result.value.length }] : [];
                    }) });
                })
                .catch(() => { if (active) { setEventAdminAccess({ email: user.email, allowed: false }); setStaffNotices({ email: user.email, events: [] }); } });
        };
        refreshAccess();
        const timer = window.setInterval(refreshAccess, 15_000);
        window.addEventListener(EVENT_ACCESS_CHANGED, refreshAccess);
        return () => {
            active = false;
            window.clearInterval(timer);
            window.removeEventListener(EVENT_ACCESS_CHANGED, refreshAccess);
        };
    }, [user, location.pathname]);

    const canManageEvents = user?.role === 'ADMIN' || (eventAdminAccess?.email === user?.email && eventAdminAccess?.allowed === true);
    const pendingStaffEvents = staffNotices?.email === user?.email ? staffNotices?.events ?? [] : [];
    const pendingStaffCount = pendingStaffEvents.reduce((total, event) => total + event.count, 0);

    const exit = async () => {
        setBusy(true);
        setError('');
        try {
            await logout();
            setOpen(false);
            navigate('/login');
        } catch {
            setError('Uscita non riuscita. Riprova.');
        } finally {
            setBusy(false);
        }
    };

    return (
        <header className="topbar">
            <Brand />
            {user && (
                <nav id="primary-navigation" aria-label="Navigazione area riservata" className={`nav-links ${navOpen ? 'is-open' : ''}`}>
                    {canManageEvents && <NavLink to="/admin/dashboard" onClick={() => setNavOpen(false)}><LayoutDashboard size={16} />Dashboard</NavLink>}
                    {canManageEvents && <NavLink to="/admin/events" onClick={() => setNavOpen(false)}><CalendarDays size={16} />Eventi</NavLink>}
                    {canManageEvents && <NavLink to="/admin/bookings" onClick={() => setNavOpen(false)}><TicketCheck size={16} />Prenotazioni</NavLink>}
                    {canManageEvents && <NavLink to="/collaborations" onClick={() => setNavOpen(false)}><UsersRound size={16} />Collaborazioni</NavLink>}
                    <NavLink to="/staff/scan" onClick={() => setNavOpen(false)}><ScanLine size={16} />Ingressi</NavLink>
                </nav>
            )}

            <div className="topbar-end">
                {user && pendingStaffCount > 0 && <div className="staff-notifications" ref={noticesMenu}>
                    <button className="icon-button staff-notification-trigger" type="button" aria-label={`${pendingStaffCount} richieste staff in attesa`} aria-expanded={noticesOpen} onClick={() => setNoticesOpen((current) => !current)}><Bell size={18} /><span className="staff-notification-count">{pendingStaffCount}</span></button>
                    {noticesOpen && <div className="staff-notification-panel"><strong>Richieste staff in attesa</strong>{pendingStaffEvents.map((event) => <NavLink key={event.id} to={`/admin/events/${event.id}/team`} onClick={() => setNoticesOpen(false)}>{event.name}<span>{event.count}</span></NavLink>)}</div>}
                </div>}
                {user && (
                    <button
                        className="icon-button mobile-nav-toggle"
                        type="button"
                        aria-label={navOpen ? 'Chiudi navigazione' : 'Apri navigazione'}
                        aria-expanded={navOpen}
                        aria-controls="primary-navigation"
                        onClick={() => setNavOpen((current) => !current)}
                    >
                        {navOpen ? <X size={20} /> : <Menu size={20} />}
                    </button>
                )}
                <button className="icon-button" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')} aria-label={theme === 'dark' ? 'Attiva tema chiaro' : 'Attiva tema scuro'}>
                    {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
                </button>

                {user && (
                    <div className="profile" ref={menu}>
                        <button className="profile-trigger" aria-expanded={open} aria-controls="profile-panel" onClick={() => setOpen(!open)}>
                            <span className="avatar">{user.name.slice(0, 1)}{user.surname.slice(0, 1)}</span>
                            <span className="profile-name">{user.name} {user.surname}<small>{user.role === 'ADMIN' ? 'Amministratore' : 'Staff'}</small></span>
                            <ChevronDown size={15} />
                        </button>
                        {open && (
                            <div id="profile-panel" className="profile-panel">
                                <strong>{user.name} {user.surname}</strong>
                                <p>{user.email}</p>
                                <span className="eyebrow">Impostazioni</span>
                                <NavLink className="profile-link" to="/account" onClick={() => setOpen(false)}><KeyRound size={15} /> Sicurezza account</NavLink>
                                {user.role === 'ADMIN' && <NavLink className="profile-link" to="/settings/mail" onClick={() => setOpen(false)}><Mail size={15} /> Configurazione mail</NavLink>}
                                <hr />
                                <span className="eyebrow">Aspetto</span>
                                <div className="theme-options">
                                    {['dark', 'light'].map((value) => <button key={value} className="button" aria-pressed={theme === value} onClick={() => setTheme(value)}>{value === 'dark' ? 'Scuro' : 'Chiaro'}</button>)}
                                </div>
                                <span className="eyebrow">Colore accento</span>
                                <div className="swatches">
                                    {['lime', 'blue', 'rose', 'orange'].map((value) => <button key={value} className={`swatch ${value}`} aria-label={`Colore ${value}`} aria-pressed={accent === value} onClick={() => setAccent(value)} />)}
                                </div>
                                <hr />
                                {error && <p role="alert" className="menu-error">{error}</p>}
                                <button className="logout-button" disabled={busy} onClick={() => void exit()}><LogOut size={16} />{busy ? 'Uscita…' : 'Esci'}</button>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </header>
    );
}
