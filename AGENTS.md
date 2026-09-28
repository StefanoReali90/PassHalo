# CrowdPass - Linee Guida e Ruolo Tutor

## 1. Ruolo dell'Assistente (Regola d'Oro)
- **Tutor / Mentore Didattico per il Backend (Java / Spring Boot)**: L'utente impara e programma autonomamente la parte server.

- **ECCEZIONE FRONTEND (Autorizzata dall'utente)**: L'assistente può scrivere, generare e modificare direttamente il codice del Frontend (React, TypeScript, HTML/CSS, componenti, pagine e configurazioni).
- **Compito per il Backend**: Fornire spiegazioni concettuali, guidare nell'architettura, indicare cosa fare passo dopo passo, segnalare edge cases, fare domande guida e revisionare il codice scritto dall'utente.

---

## 2. Requisiti di Business dell'Applicazione (CrowdPass)

### A. Prenotazione Cliente (Pubblico)
- **Scopo**: Prenotare per ottenere il prezzo ridotto all'evento (es. 10€ invece di 15€ all'ingresso).
- **Dati richiesti**: Nome, Cognome, Email.
- **Output**: Al submit, la prenotazione viene salvata e viene generato un QR Code associato a un identificativo univoco (UUID / Token).

### B. Gestione Utenze Interne (Admin & Staff)
- **Registrazione e Autenticazione**: Nome, Cognome, Email, Password.
- **Ruolo ADMIN**:
  - Accesso alla dashboard con statistiche dell'evento.
  - Verifica e lettura QR code all'ingresso (Check-in).
  - Gestione del ciclo di vita dell'evento (es. chiusura e pulizia dati).
- **Ruolo STAFF**:
  - SOLO lettura e verifica QR code all'ingresso (Check-in).
  - Riceve esito immediato: **OK** (prenotato e convalidato) oppure **KO** (non valido o già convalidato).
  - **NON ha accesso** alle statistiche o ai dati sensibili.

### C. Check-in all'Ingresso
- Scansione del QR Code.
- Verifica validità e stato della prenotazione (evitare riutilizzi doppi dello stesso QR).
- Cambio stato (es. da prenotato a convalidato/partecipato).

### D. Ciclo di Vita Post-Evento & Privacy (GDPR)
- Cancellazione o anonimizzazione dei dati personali (Nome, Cognome, Email).
- Mantenimento delle statistiche aggregate storiche (quante persone si sono prenotate, quante sono effettivamente entrate).

---

## 3. Stato dei lavori - Ripartenza sessione successiva (22 settembre 2026)

### Obiettivo architetturale concordato
- Ogni evento ha un proprietario, mantenuto nella relazione `Event.user`.
- Il proprietario non deve essere duplicato in `EventMembership`.
- Gli altri utenti possono collaborare allo stesso evento mediante `EventMembership`.
- I ruoli relativi al singolo evento sono `EVENT_ADMIN` e `STAFF`.
- Una membership puo essere `ACTIVE` oppure `REVOKED` e puo avere un periodo di validita.
- L'autorizzazione deve essere sempre relativa all'evento richiesto: il ruolo globale dell'account, da solo, non e sufficiente.
- Un `EVENT_ADMIN` collabora alla gestione dell'evento.
- Uno `STAFF` puo usare soltanto scanner QR e conteggio degli ingressi senza prenotazione per gli eventi assegnati; non puo vedere statistiche o dati personali dei clienti.

### Lavoro completato e verificato
- Creata l'entita `EventMembership` con:
  - evento;
  - collaboratore;
  - ruolo per evento;
  - stato;
  - `createdAt`, `validFrom`, `validUntil` e `revokedAt`;
  - utente che ha creato l'assegnazione.
- Configurato il vincolo composto univoco su `(event_id, collaborator_id)`.
- Le colonne `event_id` e `collaborator_id` non sono singolarmente univoche.
- Creati gli enum `EventRole`, `MembershipState` e `InviteState`.
- Creato `EventMembershipRepository` con operazioni per:
  - cercare una membership mediante evento e collaboratore;
  - elencare le membership di un evento filtrate per stato;
  - elencare le membership di un collaboratore filtrate per stato.
- Il contesto Spring riconosce cinque repository e valida correttamente tutte le derived query.
- Test verificati senza errori o fallimenti.

### Prossimo passo immediato
Creare autonomamente l'entita backend `EventInvitation`, poi farla revisionare prima di creare repository o service.

Campi e relazioni da modellare:
- identificativo;
- evento obbligatorio;
- email normalizzata del destinatario;
- ruolo proposto (`EVENT_ADMIN` oppure `STAFF`);
- stato tramite `InviteState` (`PENDING`, `ACCEPTED`, `EXPIRED`, `REVOKED`);
- hash univoco del token di invito: il token originale non deve essere salvato nel database o scritto nei log;
- data di creazione e data di scadenza obbligatorie;
- data di accettazione e data di revoca facoltative;
- utente che ha creato l'invito;
- utente che ha accettato l'invito, inizialmente assente.

Regole dell'invito:
- `event_id` ed email non devono essere singolarmente `unique`;
- l'invito nasce `PENDING`;
- l'account autenticato che accetta deve avere la stessa email normalizzata dell'invito;
- token scaduti, revocati o gia accettati non possono essere riutilizzati;
- la membership viene creata o riattivata soltanto dopo l'accettazione;
- se una membership revocata esiste gia per la coppia evento-collaboratore, deve essere riattivata e aggiornata invece di inserire un duplicato;
- non salvare mai il token di invito in chiaro.

### Passaggi successivi, nell'ordine
1. Creare e revisionare `EventInvitationRepository`.
2. Progettare il service per creazione, accettazione, revoca e scadenza degli inviti.
3. Centralizzare i controlli di accesso agli eventi in un servizio dedicato.
4. Per ogni richiesta protetta verificare proprietario oppure membership `ACTIVE`, ruolo richiesto, `validFrom` e `validUntil`.
5. Aggiornare "I miei eventi" per unire eventi posseduti ed eventi ricevuti come collaboratore, senza duplicati.
6. Applicare i controlli event-scoped a dashboard, prenotazioni, check-in, walk-in, modifica, chiusura e cancellazione.
7. Definire con precisione il flusso account STAFF e separare correttamente ruolo globale e ruolo relativo all'evento.
8. Aggiungere test di isolamento multi-tenant: nessun proprietario, admin o staff deve accedere agli eventi di altri tenant senza membership valida.
9. Aggiungere test per revoca, scadenza, doppia accettazione del token e reinvito.

### Sicurezza e privacy ancora da affrontare
- Cifrare a livello applicativo nome, cognome, email e telefono delle prenotazioni con cifratura autenticata; la sola cifratura del disco/database non protegge da un accesso al DB.
- Gestire le chiavi fuori dal database e fuori dal repository del codice.
- Se serve cercare una prenotazione per email, progettare un indice cieco separato invece di usare dati cifrati deterministicamente.
- Non restituire dati personali allo STAFF: lo scanner deve ricevere soltanto l'esito minimo necessario.
- Rendere atomico il check-in per impedire due convalide contemporanee dello stesso QR.
- Dopo la chiusura dell'evento anonimizzare i dati personali, invalidare i QR e conservare soltanto statistiche aggregate e dati strettamente necessari.
- Verificare autorizzazione e appartenenza all'evento anche negli endpoint delle prenotazioni, non soltanto nelle regole generali di Spring Security.
- Evitare token, email e dati personali nei log.

### Nota ambiente di sviluppo
- Con Java 26 il primo ciclo di compilazione puo mostrare sporadicamente `Cannot close compiler resources`; il ciclo incrementale successivo completa i test.
- Il wrapper `mvnw.cmd` ha mostrato un problema PowerShell interno relativo alla directory `.m2`; non confonderlo con errori delle entita o dei repository.
- I nuovi file risultavano `AM` in Git: prima del futuro commit bisogna aggiungerli nuovamente allo staging.
