import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ArrowLeft, Check, KeyRound, RefreshCw, ShieldCheck, UserMinus, UserPlus, X } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import {
    createInvitation, getMemberships,
    getPendingInvitations, revokeInvitation, revokeMembership,
} from '../api/collaboration';
import { getMyEvents } from '../api/events';
import { createStaffCode, decideStaffRequest, getStaffRequests, type StaffCodeResponse, type StaffRequestResponse } from '../api/staffAccess';
import { ConfirmDialog } from '../components/ConfirmDialog';
import type { EventInvitationResponse, EventMembershipResponse, EventRole, MyEvent } from '../types';

const roleLabels: Record<EventRole, string> = { EVENT_ADMIN: 'Amministratore evento', STAFF: 'Staff ingressi' };

function formatDate(value: string) {
    return new Intl.DateTimeFormat('it-IT', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}

type RevokeTarget = { kind: 'invitation' | 'membership'; id: number; label: string };

export function EventTeamPage() {
    const eventId = Number(useParams().eventId);
    const [event, setEvent] = useState<MyEvent | null>(null);
    const [invitations, setInvitations] = useState<EventInvitationResponse[]>([]);
    const [memberships, setMemberships] = useState<EventMembershipResponse[]>([]);
    const [staffRequests, setStaffRequests] = useState<StaffRequestResponse[]>([]);
    const [staffCode, setStaffCode] = useState<StaffCodeResponse | null>(null);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [revokeTarget, setRevokeTarget] = useState<RevokeTarget | null>(null);

    const loadTeam = useCallback(async () => {
        if (!Number.isInteger(eventId) || eventId <= 0) throw new Error('Evento non valido.');
        const myEvents = await getMyEvents();
        const selected = myEvents.find((item) => item.id === eventId && item.role === 'EVENT_ADMIN');
        if (!selected) throw new Error('Non hai accesso alla gestione dei collaboratori di questo evento.');
        const [pendingInvitations, eventMemberships, pendingStaff] = await Promise.all([
            getPendingInvitations(eventId),
            getMemberships(eventId),
            selected.owner ? getStaffRequests(eventId) : Promise.resolve([]),
        ]);
        return { selected, pendingInvitations, eventMemberships, pendingStaff };
    }, [eventId]);

    const refresh = useCallback(async () => {
        try {
            const data = await loadTeam();
            setError('');
            setEvent(data.selected);
            setInvitations(data.pendingInvitations);
            setMemberships(data.eventMemberships);
            setStaffRequests(data.pendingStaff);
        } catch (requestError) {
            setEvent(null);
            setError(requestError instanceof Error ? requestError.message : 'Collaboratori non disponibili.');
        } finally { setLoading(false); }
    }, [loadTeam]);

    useEffect(() => {
        let active = true;
        loadTeam()
            .then((data) => {
                if (!active) return;
                setError('');
                setEvent(data.selected);
                setInvitations(data.pendingInvitations);
                setMemberships(data.eventMemberships);
                setStaffRequests(data.pendingStaff);
            })
            .catch((requestError) => {
                if (active) setError(requestError instanceof Error ? requestError.message : 'Collaboratori non disponibili.');
            })
            .finally(() => { if (active) setLoading(false); });
        return () => { active = false; };
    }, [loadTeam]);

    useEffect(() => {
        if (!event?.owner || event.eventState === 'FINISHED') return;
        let active = true;
        const timer = window.setInterval(() => {
            void getStaffRequests(eventId).then((requests) => {
                if (active) setStaffRequests(requests);
            }).catch(() => { /* The next manual refresh shows the error. */ });
        }, 5_000);
        return () => { active = false; window.clearInterval(timer); };
    }, [event?.owner, event?.eventState, eventId]);

    const generateStaffCode = async () => {
        setBusy(true);
        setError('');
        setMessage('');
        try {
            setStaffCode(await createStaffCode(eventId));
            setMessage('Nuovo codice creato. Comunicalo allo staff: sarà visibile solo in questa pagina finché rimane aperta.');
            setStaffRequests([]);
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Codice staff non creato.');
        } finally { setBusy(false); }
    };

    const decideStaff = async (requestId: number, decision: 'approve' | 'reject') => {
        setBusy(true);
        setError('');
        setMessage('');
        try {
            await decideStaffRequest(eventId, requestId, decision);
            setStaffRequests((current) => current.filter((request) => request.id !== requestId));
            setMessage(decision === 'approve' ? 'Accesso staff approvato.' : 'Richiesta staff rifiutata.');
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Decisione non salvata.');
        } finally { setBusy(false); }
    };

    const invite = async (submitEvent: FormEvent<HTMLFormElement>) => {
        submitEvent.preventDefault();
        const form = submitEvent.currentTarget;
        const data = new FormData(form);
        setBusy(true);
        setError('');
        setMessage('');
        try {
            await createInvitation(eventId, String(data.get('email')).trim(), 'EVENT_ADMIN');
            form.reset();
            setMessage('Invito inviato via email. Il collaboratore dovrà accettarlo dal proprio account.');
            await refresh();
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Invito non riuscito.');
        } finally { setBusy(false); }
    };

    const revoke = async () => {
        if (!revokeTarget) return;
        setBusy(true);
        setError('');
        setMessage('');
        try {
            if (revokeTarget.kind === 'invitation') await revokeInvitation(eventId, revokeTarget.id);
            else await revokeMembership(eventId, revokeTarget.id);
            setMessage(revokeTarget.kind === 'invitation' ? 'Invito revocato.' : 'Accesso revocato.');
            setRevokeTarget(null);
            await refresh();
        } catch (requestError) {
            setError(requestError instanceof Error ? requestError.message : 'Revoca non riuscita.');
        } finally { setBusy(false); }
    };

    return (
        <section className="workspace-page collaboration-page">
            <Link className="text-link collaboration-back" to="/admin/events"><ArrowLeft size={16} /> Torna agli eventi</Link>
            <div className="page-heading"><div><span className="eyebrow">Amministrazione / Collaboratori</span><h1>{event?.name ?? 'Collaboratori'}<span className="accent-text">.</span></h1><p>Gestisci gli accessi delle persone che lavorano a questo evento.</p></div><button className="button" onClick={() => { setLoading(true); void refresh(); }} disabled={loading}><RefreshCw size={16} className={loading ? 'spinning' : ''} /> Aggiorna</button></div>
            {error && <div className="notice error" role="alert">{error}</div>}
            {message && <div className="notice success" role="status">{message}</div>}
            {!event || loading ? loading && <div className="panel empty-state" role="status">Caricamento collaboratori…</div> : <>
                {event.owner && <section className="collaboration-section staff-management">
                    <div className="result-heading"><div><span className="eyebrow">Accesso temporaneo</span><h2>Staff senza account</h2></div></div>
                    <div className="collaboration-forms single"><article className="panel collaboration-panel"><span className="eyebrow"><KeyRound size={14} /> Codice dell’evento</span><h2>Genera un codice per lo staff</h2><p>Lo staff lo inserisce nella pagina pubblica. L’accesso si attiva solo dopo la tua approvazione e termina con l’evento. Generare un altro codice interrompe anche le sessioni staff già approvate.</p><button className="button primary" disabled={busy || event.eventState === 'FINISHED'} onClick={() => void generateStaffCode()}>{staffCode ? 'Rigenera codice' : 'Genera codice'}</button>{staffCode && <div className="staff-code-result" role="status"><label htmlFor="generated-staff-code">Comunica questo codice allo staff</label><input id="generated-staff-code" value={staffCode.code} readOnly onFocus={(inputEvent) => inputEvent.currentTarget.select()} /><small>Valido fino al {formatDate(staffCode.expiresAt)}. Non sarà mostrato di nuovo dopo aver lasciato questa pagina.</small></div>}</article></div>
                    <div className="result-heading staff-requests-heading"><div><span className="eyebrow">In attesa della tua decisione</span><h2>Richieste staff ({staffRequests.length})</h2></div></div>
                    {staffRequests.length === 0 ? <div className="panel empty-state"><p>Nessuna richiesta in attesa.</p></div> : <div className="collaboration-event-list">{staffRequests.map((request) => <article className="panel collaboration-event" key={request.id}><div><strong>Dispositivo staff #{request.id}</strong><p>Richiesta ricevuta il {formatDate(request.createdAt)}</p></div><div className="collaboration-actions"><button className="button" disabled={busy} onClick={() => void decideStaff(request.id, 'reject')}><X size={15} /> Rifiuta</button><button className="button primary" disabled={busy} onClick={() => void decideStaff(request.id, 'approve')}><Check size={15} /> Approva</button></div></article>)}</div>}
                </section>}
                <div className="collaboration-forms single">
                    <article className="panel collaboration-panel">
                        <span className="eyebrow"><UserPlus size={14} /> Collaborazione amministrativa</span><h2>Invita un amministratore</h2><p>L’invito contiene un codice personale che può essere accettato solo dall’account con questa email.</p>
                        <form onSubmit={invite}><label htmlFor="invite-email">Email del collaboratore</label><input id="invite-email" name="email" type="email" required /><button className="button primary" disabled={busy || event.eventState === 'FINISHED'}>Invia invito</button></form>
                    </article>
                </div>
                <section className="collaboration-section"><div className="result-heading"><div><span className="eyebrow">In attesa di accettazione</span><h2>Inviti email ({invitations.length})</h2></div></div>{invitations.length === 0 ? <div className="panel empty-state"><p>Nessun invito in attesa.</p></div> : <div className="collaboration-event-list">{invitations.map((invitation) => <article className="panel collaboration-event" key={invitation.id}><div><strong>{invitation.recipientEmail}</strong><p>{roleLabels[invitation.proposedRole]} · Scade il {formatDate(invitation.expiresAt)}</p></div><button className="button danger" disabled={busy} onClick={() => setRevokeTarget({ kind: 'invitation', id: invitation.id, label: invitation.recipientEmail })}><X size={15} /> Revoca</button></article>)}</div>}</section>
                <section className="collaboration-section"><div className="result-heading"><div><span className="eyebrow">Accessi all’evento</span><h2>Collaboratori ({memberships.filter((membership) => membership.state === 'ACTIVE').length})</h2></div></div>{memberships.length === 0 ? <div className="panel empty-state"><p>Nessun collaboratore assegnato.</p></div> : <div className="collaboration-event-list">{memberships.map((membership) => <article className="panel collaboration-event" key={membership.id}><div><strong>{membership.role === 'STAFF' ? `Accesso staff #${membership.id}` : `${membership.collaboratorName} ${membership.collaboratorSurname}`}</strong><p>{membership.role === 'STAFF' ? '' : `${membership.collaboratorEmail} · `}{roleLabels[membership.role]} · {membership.state === 'ACTIVE' ? 'Attivo' : 'Revocato'}</p>{membership.validUntil && <small>Valido fino al {formatDate(membership.validUntil)}</small>}</div>{membership.state === 'ACTIVE' ? <button className="button danger" disabled={busy} onClick={() => setRevokeTarget({ kind: 'membership', id: membership.id, label: membership.role === 'STAFF' ? `l’accesso staff #${membership.id}` : `${membership.collaboratorName} ${membership.collaboratorSurname}` })}><UserMinus size={15} /> Revoca accesso</button> : <ShieldCheck size={18} aria-label="Accesso revocato" />}</article>)}</div>}</section>
            </>}
            <ConfirmDialog open={revokeTarget !== null} title={`Revocare ${revokeTarget?.label ?? 'questa collaborazione'}?`} description={revokeTarget?.kind === 'membership' ? 'La persona perderà subito l’accesso a questo evento.' : 'Il codice dell’invito non potrà più essere accettato.'} confirmLabel="Revoca" busy={busy} onCancel={() => setRevokeTarget(null)} onConfirm={revoke} />
        </section>
    );
}
