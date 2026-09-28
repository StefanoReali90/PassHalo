# PassHalo Mobile

App React Native/Expo per Android con:

- prenotazione pubblica e visualizzazione QR;
- registrazione autonoma ADMIN senza codice condiviso;
- login ADMIN/STAFF con JWT conservato in SecureStore;
- scanner QR nativo e check-in;
- creazione degli eventi per gli account ADMIN e gestione degli eventi per cui si ha il ruolo EVENT_ADMIN;
- dashboard e prenotazioni limitate agli eventi gestiti come proprietario o EVENT_ADMIN;
- scanner disponibile soltanto per gli eventi assegnati allo staff;
- URL del server modificabile direttamente dall'app.

Per preparare il VPS e gli aggiornamenti automatici del backend/web, vedi la [guida al deploy](../../deploy/README.md). Il profilo APK `preview` in `eas.json` usa `https://passhalo.it/api`; il file `.env` serve allo sviluppo locale.

## Avvio locale

È richiesta una versione Node compatibile con Expo SDK 57/React Native 0.86 (ad esempio Node 24.3 o più recente).

```powershell
cd mobile
Copy-Item .env.example .env
npm install
npm run start
```

Installa Expo Go sul telefono e scansiona il QR mostrato dal terminale. In alternativa configura il server dalla scheda **Server** dell'app. Se Cloudflare punta al frontend Vite, l'URL deve terminare con `/api`.

## Generare un APK

La prima volta:

```powershell
npx eas-cli@latest login
npx eas-cli@latest build:configure
```

Poi:

```powershell
npm run build:apk
```

Il profilo `preview` in `eas.json` genera un APK installabile direttamente. EAS mostrerà il link per scaricarlo al termine della build.

## Verifiche

```powershell
npm run typecheck
npm run doctor
```
