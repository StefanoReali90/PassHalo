# Avvio locale del backend

Spring Boot non carica automaticamente `backend/.env`. Per rendere disponibili le
credenziali quando avvii `PassHaloApplication` da IntelliJ:

1. Tieni le credenziali locali in `backend/.env`, che Git ignora.
2. Apri PowerShell nella cartella `backend` ed esegui `./prepare-local-config.ps1`.
3. Avvia l'applicazione da IntelliJ. La configurazione funziona con la
   *working directory* sulla radice del progetto oppure su `backend`.

Lo script crea `.local.env`, che Spring Boot legge come file di proprieta' e che
Git ignora. Genera `PII_ENCRYPTION_KEY` e `PII_LOOKUP_KEY` solo se assenti e le
salva anche in `.env`. Conserva queste chiavi: servono a leggere i dati cifrati.
Per lo sviluppo locale imposta inizialmente `FRONTEND_BASE_URL` a
`http://localhost:5173`. Riesegui lo script dopo ogni modifica di `.env`.

**Database vuoto:** avvia una sola volta con `HIBERNATE_DDL_AUTO=update` per
creare le tabelle. Poi rimuovi questa variabile e riavvia normalmente.

**Database con dati esistenti e vecchio schema:** segui
`PERSONAL_DATA_MIGRATION.md` prima di avviare la versione corrente. L'avvio
ordinario valida lo schema e si interrompe se mancano le colonne per i dati
cifrati; non lo modifica automaticamente. Durante la migrazione controllata
si abilitano `HIBERNATE_DDL_AUTO=update` e `PII_MIGRATION_ENABLED=true`.
Questi valori non vanno lasciati attivi nell'avvio ordinario.

Per i database gia' creati prima dell'ampliamento dei contenuti evento, esegui
`db/migrations/20260927_widen_event_content.sql`: amplia la descrizione e
l'URL dell'immagine. La sola validazione dello schema potrebbe non rilevare
una differenza nella lunghezza delle colonne.

Per un'installazione pubblica, configura le chiavi tramite un gestore di segreti;
questo script e' destinato soltanto alla macchina di sviluppo.

Per i database creati prima del conteggio Contanti/Carta, esegui prima del riavvio
`db/migrations/20261004_payment_method_counts.sql`. Aggiunge il metodo alle
prenotazioni e i due contatori agli eventi. Gli ingressi storici rimangono
"Metodo non registrato". Aggiorna insieme backend, web e app mobile: i nuovi
endpoint di ingresso richiedono il campo JSON `paymentMethod` (`CASH` o `CARD`).
