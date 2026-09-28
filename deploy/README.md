# Pubblicare PassHalo su un VPS Ubuntu con GitHub Actions

Il repository è su GitHub: qui le *merge request* si chiamano **pull request**.
La pipeline [ci-deploy.yml](../.github/workflows/ci-deploy.yml) funziona così:

1. Una pull request verso `main` esegue test backend, lint/build web e controllo TypeScript mobile. Non usa credenziali del server.
2. Ogni `push` su `main`, incluso il merge, ripete i controlli. Se tutti passano e `DEPLOY_ENABLED=true`, compila la versione approvata e la invia al VPS. Proteggi `main` da push diretti se vuoi che ogni pubblicazione passi da una pull request.
3. Il VPS attiva la nuova versione, verifica `GET /events` e ripristina quella precedente se l'API non parte.

L'APK non viene ricompilata a ogni merge. Compilala con `EXPO_PUBLIC_API_URL=https://passhalo.it/api`: gli aggiornamenti compatibili del backend e del sito non richiedono una nuova installazione Android. Una modifica al codice nativo o una modifica incompatibile delle API richiede una nuova APK.

> **Stato attuale:** i file sono preparati nel progetto, ma non è stato acquistato né configurato un VPS. `DEPLOY_ENABLED` deve restare assente fino alla sezione **Prima pubblicazione**. Puoi completare un punto alla volta; nessun comando seguente è stato eseguito sul tuo server.

### Dati da tenere a portata di mano

Annota in un gestore di password: IP del VPS, utente iniziale indicato dal provider, password PostgreSQL, credenziali SMTP e chiavi di cifratura. Nei comandi sotto, sostituisci `IP_DEL_VPS` e `UTENTE_INIZIALE` con i valori reali. Gli esempi che iniziano con `PS>` vanno eseguiti sul tuo Windows PowerShell; gli altri comandi vanno eseguiti nella sessione SSH Ubuntu.

## 1. Prima dell'acquisto

- Scegli un VPS Ubuntu x86_64 con almeno 4 GB RAM; 8 GB lascia più margine a Java e PostgreSQL.
- Scegli fatturazione mensile se vuoi poter interrompere il servizio tra un periodo di test e l'altro.
- Prepara un indirizzo SMTP esterno sulla porta 587. L'app invia email per pass, inviti e recupero password.
- Conserva separatamente un backup dei dati e delle chiavi PII: perdere le chiavi rende illeggibili i dati cifrati.
- Per un repository GitHub privato, GitHub Free include un numero mensile di minuti Actions; controlla **Settings → Billing and licensing** se fai molte build. In un repository pubblico i runner standard non consumano minuti a pagamento. [Condizioni GitHub Actions](https://docs.github.com/en/billing/concepts/product-billing/github-actions)

### Ordine consigliato

1. Acquista il VPS con Ubuntu e salva IP e credenziali iniziali. **Non acquistare un impegno annuale** se vuoi poterlo interrompere dopo i test.
2. Verifica di poter entrare via SSH da Windows:

   ```powershell
   ssh UTENTE_INIZIALE@IP_DEL_VPS
   ```

3. Prosegui con i punti 2 e 3 di questa guida. La prima pull request che contiene la pipeline va unita a `main` con `DEPLOY_ENABLED` ancora assente.

## 2. Preparazione del VPS (una volta)

1. Nel pannello DNS del dominio crea o aggiorna il record `A` per `@` con l'IPv4 del VPS. Elimina eventuali record `A` o `AAAA` di `@` che puntano a un altro server. Se il DNS è su Cloudflare, tieni inizialmente il record su **DNS only** (nuvola grigia); potrai attivare il proxy dopo il primo test. Nel firewall del provider abilita le porte `22/tcp`, `80/tcp` e `443/tcp`. Se Ubuntu usa anche `ufw`, apri le stesse porte prima di attivarlo. Non esporre `5432` (PostgreSQL) né `8080` (Spring Boot). Dal PowerShell locale verifica con `Resolve-DnsName passhalo.it` che l'IPv4 restituito sia quello del VPS; se è diverso, attendi la propagazione DNS. Caddy emette e rinnova il certificato HTTPS quando il DNS punta al VPS.
2. Accedi via SSH e installa PostgreSQL e i pacchetti di base:

   ```bash
   sudo apt update
   sudo apt install -y postgresql curl wget gpg apt-transport-https openssl
   ```

   Installa Eclipse Temurin **26** con il repository [Adoptium](https://adoptium.net/installation/linux/):

   ```bash
   wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
   echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
   sudo apt update
   sudo apt install -y temurin-26-jdk
   ```

   Verifica:

   ```bash
   java -version
   ```

   Deve comparire Java 26. Installa Caddy seguendo i comandi della [guida ufficiale](https://caddyserver.com/docs/install#debian-ubuntu-raspbian):

   ```bash
   sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
   curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
   curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
   sudo chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg
   sudo chmod o+r /etc/apt/sources.list.d/caddy-stable.list
   sudo apt update
   sudo apt install -y caddy
   caddy version
   ```
3. Crea il ruolo e il database PostgreSQL. Da un terminale sul VPS:

   ```bash
   sudo -u postgres createuser --pwprompt passhalo
   sudo -u postgres createdb -O passhalo passhalo
   ```

4. Crea due utenti Linux: `passhalo` esegue il servizio; `deploy` riceve i pacchetti dalla pipeline. Prepara le cartelle:

   ```bash
   sudo adduser --system --group --home /opt/passhalo passhalo
   sudo adduser --disabled-password --gecos '' deploy
   sudo install -d -m 755 /opt/passhalo /opt/passhalo/releases
   sudo chown root:root /opt/passhalo
   sudo install -d -o deploy -g deploy -m 755 /opt/passhalo/incoming
   sudo install -d -o passhalo -g passhalo -m 750 /opt/passhalo/logs
   sudo install -d -m 700 /etc/passhalo
   ```

5. **Dal PowerShell locale, nella cartella del repository**, copia i quattro file di esempio sul VPS:

   ```powershell
   scp .\deploy\passhalo.service .\deploy\Caddyfile .\deploy\passhalo-deploy.sh .\deploy\passhalo.env.example UTENTE_INIZIALE@IP_DEL_VPS:/tmp/
   ```

   Torna nella sessione SSH e installali nelle posizioni definitive:

   ```bash
   sudo install -o root -g root -m 644 /tmp/passhalo.service /etc/systemd/system/passhalo.service
   sudo install -o root -g root -m 644 /tmp/Caddyfile /etc/caddy/Caddyfile
   sudo install -o root -g root -m 755 /tmp/passhalo-deploy.sh /usr/local/sbin/passhalo-deploy
   sudo install -o root -g root -m 600 /tmp/passhalo.env.example /etc/passhalo/passhalo.env
   sudo nano /etc/passhalo/passhalo.env
   ```

   Sostituisci **tutti** i valori `replace-with-...`. Per `PII_ENCRYPTION_KEY` e `PII_LOOKUP_KEY`, esegui `openssl rand -base64 32` **due volte** e usa un risultato diverso per ciascuna. Per `JWT_SECRET_KEY`, esegui `openssl rand -hex 32`. Conserva le due chiavi PII anche nel backup sicuro, fuori dal VPS. Imposta `FRONTEND_BASE_URL=https://passhalo.it` e verifica che utente/password DB corrispondano a quelli del punto 3. Esci da `nano` con `Ctrl+O`, Invio, `Ctrl+X`.

6. Attiva i servizi. Il servizio `passhalo` viene abilitato all'avvio ma partirà solo quando la pipeline avrà caricato la prima versione:

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable passhalo caddy
   sudo caddy validate --config /etc/caddy/Caddyfile
   sudo systemctl restart caddy
   ```

7. Consenti all'utente `deploy` di eseguire **solo** lo script di pubblicazione con `sudo` senza password. Crea con `sudo visudo -f /etc/sudoers.d/passhalo-deploy` questa riga:

   ```text
   deploy ALL=(root) NOPASSWD: /usr/local/sbin/passhalo-deploy *
   ```

8. Sul **PowerShell locale**, genera una chiave SSH dedicata alla pipeline:

   ```powershell
   New-Item -ItemType Directory -Force "$env:USERPROFILE\.ssh" | Out-Null
   ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\passhalo_deploy" -C "passhalo-github-actions"
   Get-Content "$env:USERPROFILE\.ssh\passhalo_deploy.pub"
   ```

   Per questa chiave dedicata alla pipeline, lascia vuota la passphrase quando `ssh-keygen` la chiede: GitHub Actions non può digitarla durante il deploy. Proteggi quindi con cura il file privato e l'accesso al repository. Copia la **riga pubblica** mostrata. Sul VPS crea `/home/deploy/.ssh/authorized_keys`, incolla la riga su una sola linea e assegna permessi corretti:

   ```bash
   sudo install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
   sudo nano /home/deploy/.ssh/authorized_keys
   sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
   sudo chmod 600 /home/deploy/.ssh/authorized_keys
   ```

   Dal PowerShell locale prova `ssh -i "$env:USERPROFILE\.ssh\passhalo_deploy" deploy@IP_DEL_VPS`. Se non funziona, correggi SSH prima di abilitare il deploy. La chiave **privata** resta sul tuo PC e in GitHub Secrets; non va copiata nel repository.

9. Sempre dal PowerShell locale ottieni la riga per `DEPLOY_KNOWN_HOSTS` con `ssh-keyscan -t ed25519 IP_DEL_VPS`. Copia solo la riga che inizia con l'IP. Confronta l'impronta della chiave con quella mostrata dalla console del VPS (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`) prima di salvarla su GitHub. La pipeline rifiuta server con una host key diversa.

## 3. Configurazione GitHub (una volta)

Nel repository, apri **Settings → Secrets and variables → Actions**. Usa **New repository variable** per le variabili e **New repository secret** per i segreti. La chiave privata per `DEPLOY_SSH_KEY` va copiata **interamente**, comprese le righe `BEGIN` e `END`, dal file locale `passhalo_deploy` (senza `.pub`).

| Tipo | Nome | Valore |
| --- | --- | --- |
| Variable | `DEPLOY_HOST` | Stesso IP del VPS usato in `ssh-keyscan` |
| Variable | `DEPLOY_USER` | `deploy` |
| Variable | `PRIVACY_CONTROLLER_NAME` | Nome reale del titolare del trattamento |
| Variable | `PRIVACY_CONTACT_EMAIL` | Contatto privacy reale |
| Secret | `DEPLOY_SSH_KEY` | Chiave SSH privata dedicata al deploy |
| Secret | `DEPLOY_KNOWN_HOSTS` | Riga `known_hosts` verificata del VPS |
| Variable | `DEPLOY_ENABLED` | Inizialmente assente; imposta `true` solo dopo la preparazione del VPS |

Le password del database, di SMTP, del JWT e le chiavi PII restano **solo sul VPS** in `/etc/passhalo/passhalo.env`. Non inserirle in `VITE_*`, nel repository o nei log GitHub. Il job di deploy accetta soltanto `main`.

### Prima pubblicazione

1. Porta la pipeline su `main` con una pull request. Con `DEPLOY_ENABLED` assente, il job **Deploy to Ubuntu VPS** risulta saltato: è previsto.
2. Per il **database nuovo e vuoto**, cambia temporaneamente `HIBERNATE_DDL_AUTO=update` in `/etc/passhalo/passhalo.env` sul VPS. Non usare `update` per le versioni successive: prepara una migrazione SQL revisionata prima di un merge che modifica lo schema.
3. Crea la variabile GitHub `DEPLOY_ENABLED=true`. In **Actions → Verify and deploy PassHalo → Run workflow** seleziona `main` e avvia la pipeline. [Guida GitHub per l'avvio manuale](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow)
4. Attendi che tutti e quattro i job siano verdi. Poi, sul VPS, riporta `HIBERNATE_DDL_AUTO=validate` e riavvia `sudo systemctl restart passhalo`.
5. Verifica dal tuo PC `https://passhalo.it` e `https://passhalo.it/api/events`; quindi prova registrazione/login, prenotazione, email e check-in da un telefono. Non distribuire l'APK agli amici finché questa prova non è riuscita.

Se un job fallisce, apri **Actions**, seleziona l'esecuzione e leggi il primo errore del job rosso. Sul VPS controlla `sudo systemctl status passhalo --no-pager` e `sudo journalctl -u passhalo -n 100 --no-pager`. Non incollare segreti o dati personali nei ticket o nelle chat.

## 4. Flusso quotidiano

1. Lavora su un branch e apri una pull request verso `main`.
2. Attendi i tre controlli verdi: **Backend tests**, **Web build and lint**, **Mobile typecheck**. Configurali come controlli obbligatori nella protezione di `main`, se il piano del repository lo consente.
3. Fai il merge. La pipeline pubblica automaticamente la versione su `passhalo.it` soltanto se i controlli sul commit di `main` passano.
4. Controlla la pagina **Actions** e prova login, prenotazione, email e scansione QR da un telefono. Prima di un evento, ripeti questi controlli con anticipo.

Per fermare i deploy senza togliere i controlli CI, imposta `DEPLOY_ENABLED=false`. Per ripubblicare lo stesso commit puoi usare **Actions → Verify and deploy PassHalo → Run workflow** sul branch `main`.

### APK Android

Una volta verificato `https://passhalo.it/api/events`, apri `frontend/mobile/eas.json`: il profilo `preview` imposta `EXPO_PUBLIC_API_URL=https://passhalo.it/api` anche nella build remota. Dal PowerShell locale, nella cartella `frontend/mobile`, esegui:

```powershell
npm ci
npm run typecheck
npx eas-cli@latest login
npm run build:apk
```

Al primo avvio EAS può chiederti di creare o collegare il progetto Expo e di generare le credenziali Android: segui le domande a schermo. Il build EAS restituisce un link per scaricare l'APK. Installalo prima sul tuo telefono e prova login e scansione QR. La pipeline GitHub controlla il codice mobile, ma **non distribuisce automaticamente una nuova APK** dopo ogni merge: il backend può aggiornarsi senza reinstallare l'app solo se le API restano compatibili. Per modifiche native o API incompatibili, genera e distribuisci una nuova APK. Per lo sviluppo locale puoi usare `frontend/mobile/.env` ricavato da `.env.example`. Vedi anche il [README mobile](../frontend/mobile/README.md) e la [documentazione Expo sulle variabili nelle build EAS](https://docs.expo.dev/build/eas-json/#environment-variables).

## 5. Backup e aggiornamenti dello schema

- Il rollback dello script ripristina **JAR e frontend**, non il database.
- Esegui backup PostgreSQL regolari verso una destinazione fuori dal VPS e verifica almeno un ripristino. Conserva anche le chiavi PII e il file di configurazione in modo sicuro.
- Il backup automatico del fornitore da solo può avere una conservazione breve. Una migrazione SQL va salvata e provata prima del deploy che la richiede.
- Se il database esiste già quando unisci l'integrazione Brevo, applica **prima del merge** [`20260929_owner_brevo.sql`](../backend/db/migrations/20260929_owner_brevo.sql) dopo un backup PostgreSQL. Dal PowerShell locale copia il file con `scp backend/db/migrations/20260929_owner_brevo.sql UTENTE_INIZIALE@IP_DEL_VPS:/tmp/`; sul VPS esegui `sudo chmod 644 /tmp/20260929_owner_brevo.sql` e `sudo -u postgres psql -v ON_ERROR_STOP=1 -d passhalo -f /tmp/20260929_owner_brevo.sql`. I consensi vecchi restano senza organizzatore e non vengono sincronizzati automaticamente.
- La prima pubblicazione non può essere verificata finché non esistono VPS, dominio e credenziali GitHub. Lascia `DEPLOY_ENABLED` disattivato fino ad allora.

## 6. Collegare Brevo come organizzatore

1. Nel tuo account Brevo crea una lista dedicata alle campagne dei tuoi eventi e annota l'ID della lista. Le campagne si preparano e si inviano dentro Brevo.
2. In **Settings → SMTP & API → API Keys & MCP** crea una chiave chiamata, per esempio, `PassHalo`. Brevo mostra la chiave una sola volta. Una chiave API concede ampio accesso all'account: custodiscila come una password e revocala se sospetti una compromissione. [Guida Brevo sulle chiavi API](https://help.brevo.com/hc/en-us/articles/209467485-Create-and-manage-your-API-keys)
3. In PassHalo accedi come proprietario degli eventi e apri **Il tuo account → Il tuo account Brevo** sul web, oppure **Impostazioni → Il tuo account Brevo** nell'APK. Inserisci ID lista e chiave. PassHalo verifica che la lista sia accessibile, crea il webhook di disiscrizione, cifra la chiave e accoda i contatti con consenso valido di questo proprietario. La chiave non viene più restituita al dispositivo.
4. Controlla nel pannello Brevo che i contatti attesi compaiano nella lista. La sincronizzazione avviene in background circa ogni minuto. Se Brevo non risponde, PassHalo conserva le operazioni in attesa e riprova; il numero di contatti in attesa è visibile nella pagina Account.
5. Se la chiave scade o viene revocata, crea una nuova chiave **nello stesso account Brevo** e usa **Sostituisci chiave API** in PassHalo. La lista e i contatti in attesa restano associati all'organizzatore; la sincronizzazione riprende automaticamente. Brevo può disattivare le chiavi rimaste inutilizzate a lungo. [Guida Brevo sulle chiavi API](https://help.brevo.com/hc/en-us/articles/209467485-Create-and-manage-your-API-keys)
6. Per scollegare Brevo, attendi che i contatti in attesa siano zero e usa **Scollega Brevo**. PassHalo rimuove dalla lista i contatti ancora attivi per questo organizzatore e poi elimina webhook e chiave salvata. Se Brevo non risponde, mantiene il collegamento per evitare una cancellazione incompleta.

La revoca da PassHalo o la scadenza del consenso rimuove il contatto dalla lista configurata. La disiscrizione da una campagna Brevo arriva a PassHalo tramite il webhook e rimuove il consenso locale per quell'organizzatore. I consensi registrati prima dell'aggiunta del proprietario non vengono attribuiti automaticamente a nessun account Brevo. Ogni organizzatore deve usare il proprio account e una lista dedicata; la privacy del modulo di prenotazione indica il proprietario dell'evento e l'eventuale uso di Brevo.
