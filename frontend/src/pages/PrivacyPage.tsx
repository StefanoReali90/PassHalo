import { ArrowLeft, ExternalLink, ShieldCheck } from 'lucide-react';
import { Link } from 'react-router-dom';

const controllerName = import.meta.env.VITE_PRIVACY_CONTROLLER_NAME?.trim() || 'l’organizzatore dell’evento';
const contactEmail = import.meta.env.VITE_PRIVACY_CONTACT_EMAIL?.trim();
const marketingRetentionMonths = import.meta.env.VITE_MARKETING_RETENTION_MONTHS?.trim() || '24';

export function PrivacyPage() {
    return (
        <article className="privacy-page">
            <Link className="text-link privacy-back" to="/prenota"><ArrowLeft size={15} /> Torna alla prenotazione</Link>

            <header className="privacy-heading">
                <span className="eyebrow"><ShieldCheck size={15} /> Privacy e dati personali</span>
                <h1>Informazioni sul trattamento dei dati<span className="accent-text">.</span></h1>
                <p>Qui trovi, in modo sintetico, come vengono usati i dati inseriti per ottenere il pass PassHalo.</p>
            </header>

            <div className="panel privacy-content">
                <section>
                    <span className="privacy-index">01</span>
                    <div>
                        <h2>Titolare e contatti</h2>
                        <p>Il titolare del trattamento è <strong>{controllerName}</strong>, che determina finalità e modalità del trattamento per il proprio evento.</p>
                        {contactEmail ? (
                            <p>Per richieste sui dati personali puoi scrivere a <a className="text-link" href={`mailto:${contactEmail}`}>{contactEmail}</a>.</p>
                        ) : (
                            <p>Per richieste sui dati personali usa i recapiti comunicati dall’organizzatore insieme alle informazioni dell’evento.</p>
                        )}
                    </div>
                </section>

                <section>
                    <span className="privacy-index">02</span>
                    <div>
                        <h2>Dati raccolti e finalità</h2>
                        <p>Nome, cognome ed email sono necessari per creare e gestire la prenotazione. Il telefono è facoltativo. PassHalo genera inoltre un identificativo univoco e registra lo stato del pass per impedire utilizzi multipli.</p>
                        <p>Questi dati sono usati per fornire la prenotazione richiesta, mostrare il QR code e verificare l’ingresso all’evento.</p>
                    </div>
                </section>

                <section>
                    <span className="privacy-index">03</span>
                    <div>
                        <h2>Comunicazioni promozionali</h2>
                        <p>L’invio di aggiornamenti su eventi futuri è separato dalla prenotazione, facoltativo e basato sulla scelta espressa nell’apposita casella. Non selezionarla non impedisce di ottenere il pass.</p>
                        <p>Se acconsenti, nome, cognome ed email vengono conservati separatamente dalla prenotazione per inviarti comunicazioni future, anche dopo la chiusura dell’evento, per un massimo di {marketingRetentionMonths} mesi dal consenso. Puoi revocarlo in qualsiasi momento usando il link nell’email di conferma o contattando il titolare.</p>
                    </div>
                </section>

                <section>
                    <span className="privacy-index">04</span>
                    <div>
                        <h2>Accesso e conservazione</h2>
                        <p>I dati sono accessibili agli amministratori autorizzati. Lo staff addetto all’ingresso può soltanto verificare il QR code e ricevere l’esito del controllo.</p>
                        <p>Alla chiusura dell’evento il QR viene invalidato e i dati identificativi della prenotazione — nome, cognome, email e telefono — vengono rimossi. La registrazione conserva dati come stato, data e riferimento all’evento: la rimozione degli identificativi diretti non garantisce, da sola, l’anonimizzazione completa.</p>
                        <p>Alla revoca, i dati identificativi conservati per il marketing vengono cancellati. I dati di prenotazione seguono tempi e finalità distinti: per richiederne l’accesso o la cancellazione, contatta il titolare indicato sopra. Possono restare dati strettamente necessari per obblighi di legge o per tutelare diritti.</p>
                    </div>
                </section>

                <section>
                    <span className="privacy-index">05</span>
                    <div>
                        <h2>I tuoi diritti</h2>
                        <p>Puoi chiedere accesso, rettifica, cancellazione, limitazione, opposizione e, quando applicabile, portabilità dei dati. Puoi inoltre presentare un reclamo al Garante per la protezione dei dati personali.</p>
                        <a className="text-link" href="https://www.garanteprivacy.it/home/i-miei-diritti/diritti" target="_blank" rel="noreferrer">
                            Consulta la guida del Garante <ExternalLink size={14} />
                        </a>
                    </div>
                </section>
            </div>

            {import.meta.env.DEV && !import.meta.env.VITE_PRIVACY_CONTROLLER_NAME && (
                <p className="privacy-configuration-note" role="note">
                    Nota per l’organizzatore: prima della pubblicazione configura identità e contatto del titolare nelle variabili d’ambiente del frontend.
                </p>
            )}
        </article>
    );
}
