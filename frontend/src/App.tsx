import { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { AppErrorBoundary } from './components/AppErrorBoundary';
import { Brand } from './components/Brand';
import { Navbar } from './components/Navbar';
import { ProtectedRoute } from './components/ProtectedRoute';
import { AuthProvider } from './context/AuthContext';
import './App.css';

const AccountPage = lazy(() => import('./pages/AccountPage').then((module) => ({ default: module.AccountPage })));
const AdminDashboardPage = lazy(() => import('./pages/AdminDashboardPage').then((module) => ({ default: module.AdminDashboardPage })));
const BookingPage = lazy(() => import('./pages/BookingPage').then((module) => ({ default: module.BookingPage })));
const CollaborationsPage = lazy(() => import('./pages/CollaborationsPage').then((module) => ({ default: module.CollaborationsPage })));
const BookingsPage = lazy(() => import('./pages/BookingsPage').then((module) => ({ default: module.BookingsPage })));
const EventsPage = lazy(() => import('./pages/EventsPage').then((module) => ({ default: module.EventsPage })));
const EventTeamPage = lazy(() => import('./pages/EventTeamPage').then((module) => ({ default: module.EventTeamPage })));
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage').then((module) => ({ default: module.ForgotPasswordPage })));
const LoginPage = lazy(() => import('./pages/LoginPage').then((module) => ({ default: module.LoginPage })));
const PrivacyPage = lazy(() => import('./pages/PrivacyPage').then((module) => ({ default: module.PrivacyPage })));
const RegisterPage = lazy(() => import('./pages/RegisterPage').then((module) => ({ default: module.RegisterPage })));
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage').then((module) => ({ default: module.ResetPasswordPage })));
const StaffScanPage = lazy(() => import('./pages/StaffScanPage').then((module) => ({ default: module.StaffScanPage })));
const StaffAccessPage = lazy(() => import('./pages/StaffAccessPage').then((module) => ({ default: module.StaffAccessPage })));

const pageTitles: Array<[string, string]> = [
    ['/admin/dashboard', 'Dashboard'],
    ['/admin/events', 'Eventi'],
    ['/collaborations', 'Collaborazioni'],
    ['/admin/bookings', 'Prenotazioni'],
    ['/staff/scan', 'Controllo ingressi'],
    ['/staff/access', 'Accesso staff'],
    ['/account', 'Sicurezza account'],
    ['/prenota', 'Prenota il tuo ingresso'],
    ['/privacy', 'Privacy'],
    ['/register', 'Registrazione organizzatore'],
    ['/forgot-password', 'Recupera password'],
    ['/reset-password', 'Nuova password'],
    ['/login', 'Accedi'],
];

function DocumentMetadata() {
    const location = useLocation();

    useEffect(() => {
        const title = pageTitles.find(([path]) => location.pathname.startsWith(path))?.[1] ?? 'Pagina non trovata';
        document.title = `${title} · PassHalo`;
    }, [location.pathname]);

    return null;
}

function AppLayout() {
    return <><Navbar /><main id="main-content"><Outlet /></main></>;
}

function PublicBookingLayout() {
    return <><header className="topbar"><Brand /></header><main id="main-content"><Outlet /></main></>;
}

function RouteFallback() {
    return <div className="route-loader" role="status"><span className="spinner" />Caricamento pagina…</div>;
}

export default function App() {
    return (
        <AppErrorBoundary>
            <BrowserRouter>
                <DocumentMetadata />
                <AuthProvider>
                    <a className="skip-link" href="#main-content">Vai al contenuto</a>
                    <div className="app-shell">
                        <Suspense fallback={<RouteFallback />}>
                            <Routes>
                                <Route element={<AppLayout />}>
                                    <Route path="/" element={<Navigate to="/login" replace />} />
                                    <Route path="/login" element={<LoginPage />} />
                                    <Route path="/register" element={<RegisterPage />} />
                                    <Route path="/forgot-password" element={<ForgotPasswordPage />} />
                                    <Route path="/reset-password" element={<ResetPasswordPage />} />

                                    <Route path="/staff/scan" element={<ProtectedRoute roles={['ADMIN', 'STAFF']}><StaffScanPage /></ProtectedRoute>} />
                                    <Route path="/collaborations" element={<ProtectedRoute><CollaborationsPage /></ProtectedRoute>} />
                                    <Route path="/account" element={<ProtectedRoute roles={['ADMIN', 'STAFF']}><AccountPage /></ProtectedRoute>} />
                                    <Route path="/admin/dashboard" element={<ProtectedRoute eventAdmin><AdminDashboardPage /></ProtectedRoute>} />
                                    <Route path="/admin/events" element={<ProtectedRoute eventAdmin><EventsPage /></ProtectedRoute>} />
                                    <Route path="/admin/events/:eventId/team" element={<ProtectedRoute eventAdmin><EventTeamPage /></ProtectedRoute>} />
                                    <Route path="/admin/bookings" element={<ProtectedRoute eventAdmin><BookingsPage /></ProtectedRoute>} />
                                </Route>

                                <Route element={<PublicBookingLayout />}>
                                    <Route path="/staff/access" element={<StaffAccessPage />} />
                                    <Route path="/prenota" element={<BookingPage />} />
                                    <Route path="/privacy" element={<PrivacyPage />} />
                                    <Route path="/check-in" element={<Navigate to="/prenota" replace />} />
                                    <Route path="*" element={<div className="form-page"><span className="eyebrow">404</span><h1>Pagina non trovata.</h1><p>Controlla il link che hai ricevuto.</p></div>} />
                                </Route>
                            </Routes>
                        </Suspense>
                        <footer className="site-footer"><span>PassHalo</span><span>Il tuo evento, dall’inizio all’ingresso.</span><Link to="/privacy">Privacy</Link></footer>
                    </div>
                </AuthProvider>
            </BrowserRouter>
        </AppErrorBoundary>
    );
}
