# Accesso staff senza account

## Come si usa

1. Il proprietario apre **Eventi → Collaboratori**, genera il codice staff e lo comunica allo staff. Il codice appare solo al momento della generazione.
2. Lo staff apre `/staff/access`, inserisce il codice e aspetta. Non fornisce nome, cognome, email o password.
3. Il proprietario vede un avviso nell'app e approva o rifiuta la richiesta nella pagina Collaboratori dell'evento. La pagina staff controlla automaticamente lo stato ogni cinque secondi.
4. Solo dopo l'approvazione il browser può convalidare i QR, registrare ingressi senza prenotazione e correggere quelli aggiunti da quel dispositivo.
5. La sessione termina alla fine dell'evento, alla sua chiusura anticipata, all'uscita dallo staff o quando il proprietario rigenera il codice.

Se l'evento viene eliminato, vengono eliminati anche i suoi codici e le sue sessioni staff temporanee.

L'approvazione riguarda **quel browser**, non l'identità di una persona. Se più persone conoscono lo stesso codice, il proprietario vede richieste anonime numerate. Deve condividere il codice soltanto con lo staff previsto.

## Modello e controlli

- `StaffAccessCode` conserva solo l'hash SHA-256 del codice casuale di 16 caratteri. Il codice scade dopo sette giorni o alla fine dell'evento, se questa arriva prima.
- `StaffAccessRequest` conserva l'hash di un segreto casuale diverso dal codice condiviso. Il segreto resta in un cookie `HttpOnly` e `SameSite=Strict`; sui collegamenti HTTPS è anche `Secure`. Il browser non lo legge da JavaScript.
- Una richiesta nasce `PENDING`. Lo scanner e il conteggio richiedono `APPROVED`; ogni operazione ricontrolla stato della sessione, scadenza ed evento sul server.
- Il check-in verifica che la prenotazione appartenga all'evento autorizzato e usa il blocco transazionale già presente per impedire la doppia convalida. La risposta mostra solo il nome dell'evento.
- Il contatore walk-in usa l'evento della sessione server: il browser non sceglie l'ID da aggiornare.
- Le operazioni che modificano lo stato richiedono l'intestazione `X-Staff-Action` e controllano l'origine quando presente. I tentativi di codice sono limitati a 30 per indirizzo IP ogni dieci minuti nel singolo processo server.
- Gli endpoint di registrazione degli account STAFF sono disabilitati. Gli account legacy già esistenti continuano a usare le API precedenti.

## API

| Endpoint | Accesso | Effetto |
| --- | --- | --- |
| `POST /events/{eventId}/staff-code` | Proprietario | Genera un codice, invalida il precedente e le sessioni staff dell'evento. |
| `POST /staff-access/requests` | Pubblico con codice | Crea la richiesta e imposta il cookie del browser. |
| `GET /staff-access/status` | Browser con cookie | Restituisce stato, ID e nome evento, scadenza. |
| `GET /events/{eventId}/staff-requests` | Proprietario | Elenca le richieste anonime in attesa. |
| `PATCH /events/{eventId}/staff-requests/{id}/approve` o `/reject` | Proprietario | Decide una richiesta ancora in attesa. |
| `POST /staff-access/check-in` con `{ "uuid": "..." }` | Browser approvato | Convalida un QR del solo evento autorizzato. |
| `POST /staff-access/walk-ins` | Browser approvato | Aumenta il conteggio dell'evento autorizzato. |
| `POST /staff-access/walk-ins/decrement` | Browser approvato | Corregge il conteggio dell'evento autorizzato, senza scendere sotto zero. |
| `POST /staff-access/logout` | Browser con cookie | Invalida la sessione e cancella il cookie. |

Per distribuire più istanze del backend, il limite dei tentativi va spostato in uno storage condiviso. In produzione il frontend e l'API vanno serviti tramite HTTPS e con origini consentite ristrette; il proxy deve trasmettere correttamente protocollo e host.
