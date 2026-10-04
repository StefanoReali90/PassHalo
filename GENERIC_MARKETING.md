# Marketing indipendente dal fornitore

## Comportamento

In **Impostazioni / Email / Contatti per le campagne** si sceglie tra:

- **CSV**: download dei contatti dell'organizzatore autenticato che hanno un
  consenso marketing attivo e non scaduto. Il file si importa nel servizio di
  campagne scelto, se quel servizio supporta l'importazione CSV. Nessun account
  Brevo o altro collegamento API e necessario.
- **Brevo**: sincronizzazione automatica tramite l'integrazione dedicata esistente,
  comprese le rimozioni dopo revoca/scadenza e il webhook delle disiscrizioni.

Il CSV UTF-8 usa virgole, campi quotati, terminatori CRLF e BOM. Colonne:
`email`, `first_name`, `last_name`, `consent_at`, `consent_expires_at`.
Le date sono quelle locali del backend in formato ISO 8601 senza offset, come
memorizzate nel modello attuale. Nell'importatore del provider si associano le
colonne ai campi richiesti; le due date possono essere ignorate se non supportate.
Le celle che potrebbero essere interpretate come formule nei fogli di calcolo
sono neutralizzate con un apostrofo iniziale.

L'esportazione richiede un account ADMIN e non accetta un owner scelto dal client.
Non include contatti di altri organizzatori, consensi storici senza proprietario,
contatti inattivi, scaduti o senza una scadenza verificabile. Il download ha
`Cache-Control: no-store` e non restituisce credenziali o token di disiscrizione.

Un CSV e una fotografia dei dati al momento dell'esportazione: **non sincronizza
le liste gia importate**. L'organizzatore deve mantenere aggiornata la lista del
provider e rimuovere anche li i contatti revocati/scaduti in PassHalo. Restano da
gestire nel provider anche le sue disiscrizioni e soppressioni: importare un CSV
non deve riattivare un contatto che ha gia chiesto la disiscrizione.

## Modello e integrazioni

- `MarketingConnection` / `marketing_connection`: una connessione automatica per
  organizzatore, con `provider`, `credentials_ciphertext` e `configuration`.
  La configurazione JSON contiene identificatori e hash tecnici specifici del
  fornitore. Credenziali e token originali non vanno inseriti nella configurazione.
- `MarketingSyncJob` / `marketing_sync_job`: ogni operazione e legata alla
  connessione tramite `connection_id`, con un vincolo unico su connessione e hash
  email. Le revisioni e i tentativi in sospeso vengono conservati.
- `MarketingSyncService`: coda, tentativi e scelta di `MarketingProviderAdapter`
  tramite il codice del provider; non chiama direttamente le API di Brevo.
- `BrevoMarketingAdapter`, `BrevoSettings`, `BrevoApiClient` e
  `BrevoConnectionService`: dettagli API, credenziali, liste e webhook di Brevo.

Sono supportati **CSV per servizi che permettono l'importazione** e
**sincronizzazione automatica con Brevo**. Non sono dichiarate integrazioni
automatiche con fornitori non implementati. Un provider sconosciuto non riceve
contatti tramite Brevo: il job resta in attesa anziche andare al servizio sbagliato.

Per aggiungere un'integrazione automatica implementare `MarketingProviderAdapter`,
la configurazione/connessione/verifica credenziali e le disiscrizioni del relativo
provider, quindi aggiungere il form alle impostazioni e testare isolamento,
scadenza, revoca, tentativi e soppressioni. Le API dei fornitori non sono
intercambiabili. Il contratto SMTP per conferme QR e inviti resta generico.

Endpoint download: `GET /marketing/contacts.csv`. Gli endpoint specifici
`/marketing/brevo` restano compatibili, incluso il webhook gia registrato.
I nuovi consensi hanno versione `owner-email-marketing-v2`; le versioni dei
consensi storici non vengono modificate dalla migrazione.

## Aggiornare un database esistente

Prerequisito: schema di `backend/db/migrations/20260929_owner_brevo.sql` gia
applicato. Usare `backend/db/migrations/20261004_generic_marketing.sql`.

1. Preparare gli artefatti del nuovo backend e frontend.
2. Arrestare il backend precedente, che usa i vecchi nomi delle tabelle.
3. Eseguire l'intero script sul database dell'ambiente interessato.
4. Avviare il backend aggiornato e pubblicare il frontend aggiornato.

La migrazione e transazionale e rieseguibile. Rinomina le tabelle e la colonna
delle credenziali, conserva i valori cifrati originali, sposta gli identificatori
Brevo nella configurazione JSON e associa i job alla connessione esistente.
Non aggiorna i consensi e non elimina contatti o job. Se un job non ha una
connessione associabile, lo script annulla l'operazione senza eliminare dati.

Verifica dopo l'esecuzione:

```sql
SELECT provider, count(*) FROM marketing_connection GROUP BY provider;
SELECT count(*) FROM marketing_sync_job;
SELECT count(*) FROM marketing_sync_job j
LEFT JOIN marketing_connection c ON c.id = j.connection_id
WHERE c.id IS NULL;
```

L'ultima query deve restituire zero. L'esecuzione della migrazione sul server e
distinta dalle verifiche su database di test/locali.

## Verifiche eseguite

- 142 test backend passati: inclusi isolamento dell'esportazione tra organizzatori,
  esclusione dei consensi scaduti/revocati, CSV con Unicode/virgolette/formule,
  accesso negato a staff e anonimi, scelta di un adapter diverso da Brevo,
  conservazione dei job falliti e mantenimento dell'integrazione Brevo esistente.
- Lint e build frontend passati.
- Migrazione verificata su PostgreSQL in schemi temporanei: conservazione di ID,
  cifrati, configurazione e revisione dei job; sequenze identity; riesecuzione;
  rollback in presenza di un job senza connessione. Schemi temporanei eliminati
  dopo la verifica; nessuna migrazione applicata alle tabelle dell'applicazione.
