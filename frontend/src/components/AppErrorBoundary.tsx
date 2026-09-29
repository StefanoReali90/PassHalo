import { Component, type ErrorInfo, type ReactNode } from 'react';
import { AlertTriangle, RefreshCw } from 'lucide-react';

interface AppErrorBoundaryProps {
    children: ReactNode;
}

interface AppErrorBoundaryState {
    failed: boolean;
}

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
    state: AppErrorBoundaryState = { failed: false };

    static getDerivedStateFromError(): AppErrorBoundaryState {
        return { failed: true };
    }

    componentDidCatch(error: Error, info: ErrorInfo) {
        const component = info.componentStack?.match(/^\s*at\s+([\w$]+)/m)?.[1] || 'sconosciuto';
        console.error('[PassHalo] Errore non gestito nell’interfaccia', {
            tipo: error.name,
            componente: component,
        });
    }

    render() {
        if (!this.state.failed) return this.props.children;

        return (
            <main className="fatal-error" role="alert">
                <AlertTriangle size={30} />
                <span className="eyebrow">Errore applicativo</span>
                <h1>Qualcosa non ha funzionato.</h1>
                <p>I dati non sono stati modificati. Ricarica la pagina per riprendere il lavoro.</p>
                <button className="button primary" onClick={() => window.location.reload()}><RefreshCw size={17} /> Ricarica PassHalo</button>
            </main>
        );
    }
}
