# PassHalo frontend

Frontend React + TypeScript per prenotazioni pubbliche, amministrazione eventi e check-in.

Per il VPS Ubuntu e gli aggiornamenti automatici dopo le pull request, segui la [guida al deploy](../deploy/README.md).

## Avvio locale

```bash
npm install
npm run dev
```

Le chiamate a `/api` vengono inoltrate al backend su `http://127.0.0.1:8080`. Per usare un altro indirizzo, copia `.env.example` in `.env.local` e modifica `API_PROXY_TARGET`. Prima della pubblicazione configura anche `VITE_PRIVACY_CONTROLLER_NAME` e `VITE_PRIVACY_CONTACT_EMAIL` con i dati reali del titolare del trattamento.

## Condivisione temporanea con Cloudflare Tunnel

Questa modalità espone un solo URL HTTPS e mantiene frontend, backend e database sul computer locale.

1. Avvia PostgreSQL e il backend sulla porta `8080`.
2. Avvia la build frontend da condividere:

   ```powershell
   npm run share
   ```

3. In un secondo terminale crea il tunnel:

   ```powershell
   cloudflared tunnel --url http://localhost:4173
   ```

4. Condividi l'indirizzo `https://...trycloudflare.com` mostrato da Cloudflare.

I due terminali e il backend devono rimanere avviati per tutta la prova. L'indirizzo cambia a ogni nuovo Quick Tunnel. Le richieste `/api` vengono inoltrate internamente al backend, quindi browser, cookie di autenticazione e fotocamera operano sullo stesso indirizzo pubblico. Non inserire credenziali SMTP o altri segreti nei file `VITE_*`: queste variabili sono incluse nel bundle destinato al browser.

## Rotte

- `/prenota?eventId=1`: prenotazione pubblica e generazione pass QR.
- `/privacy`: informativa sul trattamento dei dati della prenotazione.
- `/login`, `/register`, `/forgot-password`, `/reset-password?token=...`: autenticazione e recupero credenziali.
- `/staff/scan`: check-in, disponibile a STAFF e ADMIN.
- `/admin/dashboard`: statistiche, walk-in e chiusura evento.
- `/admin/events`: creazione, lettura, modifica ed eliminazione eventi.
- `/admin/bookings`: elenco, ricerche e annullamento prenotazioni; il proprietario dell'evento può accodare un nuovo invio del QR.
- `/account`: cambio password del proprio account.

Tutte le richieste autenticate usano il cookie HttpOnly `jwt` tramite `credentials: 'include'`.

## Verifiche

```bash
npm run lint
npm run build
```

Per eseguire lo smoke test completo contro un backend locale isolato (consigliato: porta `8081` con database temporaneo):

```powershell
$env:PASSHALO_ALLOW_SMOKE_WRITE='true'
$env:PASSHALO_API_BASE_URL='http://127.0.0.1:8081'
npm run test:api
```

Lo script rifiuta host non locali e richiede l’abilitazione esplicita perché crea dati di collaudo.

## Note sul contratto backend

- La landing pubblica usa i campi evento già esistenti `imageUrl` e `description` e accetta due campi opzionali aggiuntivi:

  ```json
  {
    "videoUrl": "https://cdn.example.it/evento/hero.mp4",
    "faqs": [
      {
        "question": "Devo pagare subito?",
        "answer": "No, pagherai all'ingresso."
      }
    ]
  }
  ```

  `videoUrl` può contenere un MP4/WebM diretto, YouTube o Vimeo. Se `videoUrl` manca, il frontend usa `imageUrl`; se `faqs` manca o è vuoto, genera FAQ standard dai dati dell'evento. Il backend deve restituire questi campi nelle risposte evento e accettarli nei DTO di creazione/modifica perché siano condivisi tra dispositivi.
- `BookingResponse` non contiene l'ID, ma l'annullamento richiede l'ID numerico. La schermata prenotazioni consente la ricerca e l'annullamento per ID manuale.
- Il recupero password accetta il token nella rotta `/reset-password`, ma il backend deve consegnare tale token all'utente (per esempio via email).
