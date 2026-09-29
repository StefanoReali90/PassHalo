# Primo deploy di PassHalo su OVHcloud

Questa guida parte da **un VPS nuovo**, da un repository GitHub già esistente e dal dominio `passhalo.it`. Seguila nell'ordine. I comandi sono per **Ubuntu su OVHcloud** e per **Windows PowerShell** sul tuo PC. Il sito React e l'API Spring Boot saranno sul VPS; PostgreSQL sarà sullo stesso VPS; GitHub Actions compilerà e pubblicherà le versioni successive.

**Stato:** il VPS non è ancora stato preparato e nessun comando di questa guida è stato eseguito sul server. La pipeline [ci-deploy.yml](../.github/workflows/ci-deploy.yml) pubblica solo quando la variabile GitHub `DEPLOY_ENABLED` vale `true`. Tienila assente fino al passo 9.

## Come leggere i comandi

- **PC / PowerShell**: apri PowerShell nella cartella principale del repository PassHalo sul tuo PC. Non digitare il prefisso `PS>`.
- **VPS / SSH**: digita il comando dopo esserti collegato al VPS con `ssh`. Il prompt con `ubuntu@...` ti indica che sei sul VPS. `sudo` può chiedere la password dell'utente Ubuntu: mentre la digiti non vedrai caratteri.
- Sostituisci `IP_DEL_VPS` con l'IPv4 indicato nel pannello OVHcloud. Sostituisci `UTENTE_INIZIALE` con l'utente indicato nell'email di consegna, normalmente `ubuntu`. Non digitare le parentesi o i nomi segnaposto letteralmente.
- Se un comando fallisce, fermati in quel punto e leggi l'errore. Non proseguire presumendo che il passaggio sia riuscito.

Tieni in un gestore di password l'IP, l'accesso amministrativo, la password PostgreSQL, le credenziali SMTP e le chiavi PII. Non incollare password o chiavi in chat, issue, commit o log.

## 1. Preparare servizi e accessi

Prima di ordinare il server verifica di avere:

1. Accesso amministrativo al repository GitHub e al dominio `passhalo.it`.
2. Un servizio SMTP con host, porta, username, password e mittente verificato. L'app invia conferme di prenotazione, inviti e recupero password. Assicurati anche che `booking@passhalo.it` **riceva** le richieste degli utenti: è il contatto mostrato quando il QR non arriva. Se l'email del dominio è già attiva, conserva i record DNS `MX` e gli altri record email.
3. Un gestore di password o archivio cifrato per conservare una copia delle due chiavi PII fuori dal VPS.

Su OVHcloud scegli un **VPS**, non un hosting web condiviso, con Ubuntu LTS x86_64, almeno **4 GB di RAM** e IPv4. Con 8 GB hai più margine per Java e PostgreSQL. Verifica il prezzo e le opzioni di backup nel carrello; per iniziare va bene la fatturazione mensile. Non aggiungere Plesk o cPanel: la guida usa Caddy. [Primi passi con un VPS OVHcloud](https://docs.ovhcloud.com/en/guides/bare-metal-cloud/virtual-private-servers/starting-with-a-vps).

Nel pannello OVHcloud apri **Bare Metal Cloud → Virtual private servers → il tuo VPS**. Annota l'IPv4. L'email di consegna contiene l'utente iniziale e il collegamento alla password temporanea. Se scegli una chiave SSH durante l'ordine, usa quella chiave per il primo accesso anziché la password; la guida sotto assume l'accesso iniziale con password. Al primo accesso OVHcloud può obbligarti a cambiarla e disconnetterti: ricollegati con la nuova password. [Gestione del VPS nel pannello OVHcloud](https://docs.ovhcloud.com/en/guides/bare-metal-cloud/virtual-private-servers/understand-vps-control-panel).

## 2. Entrare nel VPS e proteggere SSH

**PC / PowerShell:**

```powershell
ssh UTENTE_INIZIALE@IP_DEL_VPS
```

Al primo collegamento SSH mostra l'impronta della chiave del server. Se vuoi verificarla prima di rispondere `yes`, nel pannello OVHcloud apri la **console KVM** del VPS e sul server esegui `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`; confronta l'impronta con quella mostrata da SSH. La console KVM è disponibile dal menu del VPS e serve anche a diagnosticare un blocco della connessione SSH. [Guida KVM OVHcloud](https://docs.ovhcloud.com/en/guides/bare-metal-cloud/virtual-private-servers/using-kvm-for-vps).

Sul VPS verifica che l'utente abbia i permessi amministrativi:

```bash
whoami
sudo whoami
```

Il primo comando deve mostrare l'utente iniziale e il secondo `root`. **Non abilitare l'accesso SSH diretto come root.** Aggiorna il sistema:

```bash
sudo apt update
sudo apt upgrade -y
```

Se Ubuntu richiede un riavvio, esegui `sudo reboot`, attendi un minuto e ricollegati dal PC con `ssh UTENTE_INIZIALE@IP_DEL_VPS`.

Configura una chiave SSH **personale** per l'accesso amministrativo. **PC / PowerShell:**

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.ssh" | Out-Null
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\passhalo_admin" -C "passhalo-admin"
Get-Content "$env:USERPROFILE\.ssh\passhalo_admin.pub"
```

Per la chiave personale scegli una passphrase e custodiscila. Copia la singola riga che inizia con `ssh-ed25519`. **VPS / SSH:**

```bash
install -d -m 700 ~/.ssh
nano ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

In `nano` aggiungi la riga pubblica su una riga nuova, senza eliminare eventuali chiavi già presenti. Salva con `Ctrl+O`, Invio, poi esci con `Ctrl+X`. Apri **un secondo PowerShell** e prova `ssh -i "$env:USERPROFILE\.ssh\passhalo_admin" UTENTE_INIZIALE@IP_DEL_VPS`. Mantieni aperta la prima sessione finché il secondo accesso non funziona. Non copiare mai la chiave privata `.ssh\passhalo_admin` sul server o nel repository. [Sicurezza di base del VPS OVHcloud](https://docs.ovhcloud.com/en/guides/bare-metal-cloud/virtual-private-servers/secure-your-vps).

## 3. Puntare il dominio e aprire le porte

La zona DNS da cambiare è quella dei **nameserver effettivamente usati** dal dominio. Se il dominio usa i DNS OVHcloud, nel pannello vai su **Web Cloud → Domain names → passhalo.it → DNS zone**. Crea o modifica il record **A** del dominio principale (campo sottodominio vuoto o `@`, secondo il pannello) con l'IPv4 del VPS. Rimuovi soltanto vecchi record **A/AAAA del sito** che puntano altrove. Lascia intatti `MX`, SPF, DKIM, DMARC e gli altri record email. Se usi DNS Cloudflare o di un altro fornitore, cambia il record lì; con Cloudflare usa inizialmente **DNS only**. Non creare un record `AAAA` se non configuri anche IPv6 sul VPS. [Record A OVHcloud](https://docs.ovhcloud.com/en/guides/web-cloud/domains/dns-zone-a-record-creation).

**PC / PowerShell:**

```powershell
Resolve-DnsName passhalo.it -Type A
```

L'IPv4 restituito deve essere quello del VPS. La propagazione DNS può richiedere tempo. Caddy potrà emettere il certificato HTTPS soltanto quando il dominio raggiunge il server sulle porte 80 e 443.

Sul VPS configura il firewall locale **prima** di attivarlo:

```bash
sudo apt install -y ufw
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
sudo ufw status verbose
```

Rispondi `y` se `ufw` segnala che la sessione SSH potrebbe interrompersi. Tieni aperta la sessione attuale e prova un secondo accesso SSH dal PC. Se hai attivato anche l'**OVHcloud Network Firewall**, configura lì gli ingressi `22/tcp`, `80/tcp`, `443/tcp`; non aprire `5432` (PostgreSQL) o `8080` (Spring Boot) su Internet. Se usi una porta SSH diversa da 22, sostituiscila anche nella regola `ufw` **prima** di abilitarlo.

## 4. Installare Java 25 LTS, PostgreSQL e Caddy

**VPS / SSH:**

```bash
sudo apt install -y postgresql curl wget gpg apt-transport-https openssl
```

La pipeline compila con Java **25 LTS**, quindi sul VPS installa Java 25. Il progetto è stato portato da Java 26 perché [Adoptium ne indica la fine del supporto nel settembre 2026](https://adoptium.net/support/). Aggiungi il repository [Eclipse Adoptium](https://adoptium.net/installation/linux/):

```bash
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | sudo tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update
sudo apt install -y temurin-25-jdk
java -version
```

`java -version` deve mostrare 25. Se il pacchetto non è disponibile per la versione Ubuntu scelta, fermati: la versione Java del server deve poter eseguire il JAR compilato dalla pipeline.

Installa [Caddy](https://caddyserver.com/docs/install#debian-ubuntu-raspbian), che pubblica sito e API con HTTPS:

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

## 5. Creare il database **nuovo e vuoto**

I dati del database di sviluppo sono fittizi: **non li trasferiamo**. Crea sul VPS un database PostgreSQL vuoto. **VPS / SSH:**

```bash
sudo -u postgres createuser --pwprompt passhalo
sudo -u postgres createdb -O passhalo passhalo
```

`createuser --pwprompt` chiede due volte la password del nuovo utente database. Generala nel gestore di password e annotala: dovrà coincidere con `DB_PASSWORD` nel file del passo 7. Verifica subito la connessione, digitando quella password quando richiesta:

```bash
psql -h 127.0.0.1 -U passhalo -d passhalo -W -c 'SELECT 1;'
```

Deve apparire una riga con `1`. Il database rimane accessibile solo localmente: non modificare `postgresql.conf` per aprirlo a Internet.

## 6. Preparare utenti e cartelle del servizio

**VPS / SSH:**

```bash
sudo adduser --system --group --home /opt/passhalo passhalo
sudo adduser --disabled-password --gecos '' deploy
sudo install -d -m 755 /opt/passhalo /opt/passhalo/releases
sudo chown root:root /opt/passhalo
sudo install -d -o deploy -g deploy -m 755 /opt/passhalo/incoming
sudo install -d -o passhalo -g passhalo -m 750 /opt/passhalo/logs
sudo install -d -o root -g root -m 700 /etc/passhalo
```

`passhalo` esegue l'API; `deploy` riceve l'archivio della pipeline. L'utente `deploy` **non** deve avere accesso alle password dell'applicazione.

**PC / PowerShell, nella cartella del repository:** copia i quattro file di configurazione. Se l'utente amministrativo usa la chiave personale, aggiungi `-i "$env:USERPROFILE\.ssh\passhalo_admin"` subito dopo `scp`.

```powershell
scp .\deploy\passhalo.service .\deploy\Caddyfile .\deploy\passhalo-deploy.sh .\deploy\passhalo.env.example UTENTE_INIZIALE@IP_DEL_VPS:/tmp/
```

**VPS / SSH:**

```bash
sudo install -o root -g root -m 644 /tmp/passhalo.service /etc/systemd/system/passhalo.service
sudo install -o root -g root -m 644 /tmp/Caddyfile /etc/caddy/Caddyfile
sudo install -o root -g root -m 755 /tmp/passhalo-deploy.sh /usr/local/sbin/passhalo-deploy
sudo install -o root -g root -m 600 /tmp/passhalo.env.example /etc/passhalo/passhalo.properties
```

La configurazione reale è **solo** `/etc/passhalo/passhalo.properties` sul VPS. Il file `deploy/passhalo.env.example` è un modello senza valori reali. Il servizio [passhalo.service](passhalo.service) usa `LoadCredential`: systemd consegna una copia leggibile dal processo e Spring Boot ne carica le proprietà. [Documentazione systemd sulle credenziali](https://systemd.io/CREDENTIALS/).

## 7. Compilare i secret sul VPS

Nel terminale **VPS / SSH**, esegui un comando per volta e copia ogni risultato nel gestore di password e nella riga corrispondente del file. I risultati sono secret: non fotografarli e non incollarli in chat.

```bash
openssl rand -hex 32
openssl rand -base64 32
openssl rand -base64 32
```

Il primo risultato è `JWT_SECRET_KEY`. I due risultati Base64, **diversi tra loro**, sono `PII_ENCRYPTION_KEY` e `PII_LOOKUP_KEY`. Crea un nuovo valore solo perché il database è vuoto. Dopo che saranno stati scritti dati reali, perdere `PII_ENCRYPTION_KEY` renderà illeggibili i dati cifrati; cambiare le chiavi PII richiederà una migrazione di ricifratura.

Apri il file:

```bash
sudo nano /etc/passhalo/passhalo.properties
```

Sostituisci **ogni** `replace-with-...` con i valori reali. Usa la stessa password PostgreSQL del passo 5. Per SMTP inserisci host, porta, username, password e un `MAIL_FROM` verificato dal fornitore. `FRONTEND_BASE_URL` deve essere `https://passhalo.it`; `SERVER_ADDRESS` deve restare `127.0.0.1`; `HIBERNATE_DDL_AUTO` per la **prima partenza su DB vuoto** va temporaneamente impostato a `update`. Le password con `\` o caratteri di controllo richiedono escaping nel formato Java `.properties`: per DB e JWT usa valori casuali esadecimali e controlla con il fornitore SMTP come riportare la sua password.

Salva con `Ctrl+O`, Invio, `Ctrl+X`. Verifica i permessi senza stampare il contenuto:

```bash
sudo stat -c '%a %U:%G %n' /etc/passhalo/passhalo.properties
```

Deve mostrare `600 root:root`. Conserva fuori dal VPS **almeno** un backup cifrato delle due chiavi PII, della password DB e delle credenziali SMTP. Non usare variabili `VITE_*` o `EXPO_PUBLIC_*` per questi valori: finiscono nel codice distribuito ai client.

## 8. Attivare Caddy e preparare il deploy automatico

**VPS / SSH:**

```bash
sudo systemd-analyze verify /etc/systemd/system/passhalo.service
sudo systemctl daemon-reload
sudo systemctl enable passhalo caddy
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl restart caddy
sudo systemctl status caddy --no-pager
```

`passhalo` è **abilitato** ma non può ancora partire: il JAR arriverà con la prima pubblicazione. Caddy può inizialmente rispondere con una pagina vuota o 404, perché il frontend non è stato ancora caricato. Se Caddy non parte, controlla `sudo journalctl -u caddy -n 100 --no-pager`, DNS e porte 80/443.

Consenti a `deploy` di eseguire solo lo script di pubblicazione come root. **VPS / SSH:**

```bash
sudo visudo -f /etc/sudoers.d/passhalo-deploy
```

Nel file inserisci **esattamente** questa riga e salva:

```text
deploy ALL=(root) NOPASSWD: /usr/local/sbin/passhalo-deploy *
```

`visudo` valida la sintassi. Lo script accetta solo un commit SHA di 40 caratteri.

Ora crea una **seconda** chiave SSH, dedicata solo alla pipeline. **PC / PowerShell:**

```powershell
ssh-keygen -t ed25519 -f "$env:USERPROFILE\.ssh\passhalo_deploy" -C "passhalo-github-actions"
Get-Content "$env:USERPROFILE\.ssh\passhalo_deploy.pub"
```

Per questa chiave lascia la passphrase vuota, perché GitHub Actions non può digitarla. Custodisci il file privato `passhalo_deploy`. Copia **solo la riga pubblica**. **VPS / SSH:**

```bash
sudo install -d -o deploy -g deploy -m 700 /home/deploy/.ssh
sudo nano /home/deploy/.ssh/authorized_keys
sudo chown deploy:deploy /home/deploy/.ssh/authorized_keys
sudo chmod 600 /home/deploy/.ssh/authorized_keys
```

In `nano` incolla la riga pubblica, salva ed esci. **PC / PowerShell:**

```powershell
ssh -i "$env:USERPROFILE\.ssh\passhalo_deploy" deploy@IP_DEL_VPS
```

Deve aprire una sessione come `deploy`. In quella sessione esegui `sudo -n -l`: deve elencare `/usr/local/sbin/passhalo-deploy *` senza chiedere una password. Esci con `exit`. Se non funziona, correggi SSH o la riga `visudo` prima di abilitare la pipeline.

Rileva ora la chiave pubblica **del server** per GitHub. **PC / PowerShell:**

```powershell
$knownHostsPath = Join-Path $env:TEMP 'passhalo-known-hosts'
ssh-keyscan -t ed25519 IP_DEL_VPS 2>$null | Set-Content -Encoding ascii $knownHostsPath
Get-Content $knownHostsPath
ssh-keygen -lf $knownHostsPath
```

Copia la riga che inizia con l'IP. L'ultimo comando mostra l'impronta della **stessa riga salvata**: confrontala con quella che ottieni dalla console KVM o dalla tua sessione amministrativa sul VPS con `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`. Se non coincidono, fermati. La chiave del server (`DEPLOY_KNOWN_HOSTS`) è distinta dalla chiave privata del deploy (`DEPLOY_SSH_KEY`).

## 9. Configurare GitHub e fare la prima pubblicazione

Nel repository GitHub apri **Settings → Secrets and variables → Actions**. Nella scheda **Variables**, usa **New repository variable**; nella scheda **Secrets**, usa **New repository secret**. Se non vedi Settings, verifica i permessi del tuo account. [Guida GitHub ai secret](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets).

| Tipo | Nome | Valore |
| --- | --- | --- |
| Variable | `DEPLOY_HOST` | IPv4 del VPS |
| Variable | `DEPLOY_USER` | `deploy` |
| Variable | `PRIVACY_CONTROLLER_NAME` | Nome reale del titolare del trattamento |
| Variable | `PRIVACY_CONTACT_EMAIL` | Contatto privacy reale |
| Secret | `DEPLOY_SSH_KEY` | **Tutto** il file privato `passhalo_deploy`, incluse le righe `BEGIN`/`END` |
| Secret | `DEPLOY_KNOWN_HOSTS` | Riga `ssh-keyscan` verificata del VPS |

Per copiare il secret privato dal PC senza mostrarlo nella cronologia del terminale: `Get-Content -Raw "$env:USERPROFILE\.ssh\passhalo_deploy" | Set-Clipboard`, poi incollalo nel campo `DEPLOY_SSH_KEY`. Svuota gli appunti dopo aver salvato il secret: `Set-Clipboard -Value ''`. Le password DB/SMTP e le chiavi JWT/PII rimangono nel file protetto sul VPS; **non** sono secret GitHub.

1. **Prima porta il codice su GitHub.** Nel PowerShell del repository esegui `git status` e controlla che non vi siano file con secret reali. Se sei già sul branch di lavoro, esegui `git add .`, `git commit -m "Prepare OVHcloud deployment"`, `git push -u origin HEAD`. Se il branch è `main`, crea prima un branch con `git switch -c prepare-deploy`. GitHub mostrerà **Compare & pull request**: aprila verso `main` e attendi verdi **Backend tests**, **Web build and lint**, **Mobile typecheck**. Poi fai il merge. Finché `DEPLOY_ENABLED` è assente, il job **Deploy to Ubuntu VPS** sarà saltato: è previsto.
2. Sul VPS verifica ancora che `HIBERNATE_DDL_AUTO=update` sia impostato nel file protetto. Questo **primo avvio** su DB vuoto creerà lo schema. I dati fittizi del DB locale non vengono copiati. Gli script in `backend/db/migrations` servono per **database già esistenti**; non vanno eseguiti sul nuovo database vuoto.
3. In GitHub crea la repository variable `DEPLOY_ENABLED` con valore esatto `true`. Apri **Actions → Verify and deploy PassHalo → Run workflow**, scegli `main` e avvia. [Avvio manuale di un workflow GitHub](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow). La pipeline ripete i tre controlli, crea il JAR e il sito, carica l'archivio e avvia il servizio.
4. Attendi che **tutti e quattro** i job siano verdi. Sul VPS controlla `sudo systemctl status passhalo --no-pager` e `sudo journalctl -u passhalo -n 100 --no-pager`. Se il deploy fallisce, non impostare ancora `validate`: usa la sezione Problemi frequenti.
5. Solo dopo il primo avvio riuscito, sul VPS riapri `sudo nano /etc/passhalo/passhalo.properties`, rimetti `HIBERNATE_DDL_AUTO=validate`, salva e lancia `sudo systemctl restart passhalo`. Controlla di nuovo che il servizio sia `active (running)`.

La pipeline pubblica in automatico ogni successivo merge su `main` finché `DEPLOY_ENABLED=true`. Proteggi `main` con pull request e controlli obbligatori nelle impostazioni GitHub del repository, se disponibili. Se vuoi fermare la pubblicazione senza fermare i test, imposta `DEPLOY_ENABLED=false`.

## 10. Verificare il sito prima di usarlo

**PC / PowerShell:**

```powershell
Resolve-DnsName passhalo.it -Type A
Invoke-WebRequest https://passhalo.it
Invoke-RestMethod https://passhalo.it/api/events
```

Il primo comando deve mostrare l'IPv4 del VPS; il secondo deve rispondere senza errori di certificato; il terzo deve restituire la lista degli eventi, anche vuota. Apri poi `https://passhalo.it` nel browser e prova in quest'ordine:

1. Registrazione di un account e login.
2. Creazione di un **evento di prova**.
3. Prenotazione pubblica con un indirizzo email che controlli; verifica l'arrivo del QR e anche la cartella spam. In caso di mancato invio, il sito indica `booking@passhalo.it`.
4. Scansione del QR da telefono; una seconda scansione dello stesso QR deve dare esito già convalidato.
5. Eventuali funzioni staff e inviti con un account separato.

Non distribuire l'APK agli amici prima di questi controlli. Il rollback automatico del deploy ripristina JAR e frontend precedenti se l'API non risponde a `/events`; **non** ripristina il database.

## 11. Leggere log e risolvere i problemi più comuni

**VPS / SSH:**

```bash
sudo systemctl status passhalo --no-pager
sudo journalctl -u passhalo -n 100 --no-pager
sudo journalctl -u passhalo -f
sudo tail -n 100 /opt/passhalo/logs/passhalo.log
sudo systemctl status caddy --no-pager
sudo journalctl -u caddy -n 100 --no-pager
```

`journalctl -f` resta in ascolto; termina con `Ctrl+C`. I log API includono un `requestId`, utile per collegare una richiesta ai messaggi relativi. Non pubblicare log con dati personali o secret.

| Sintomo | Cosa controllare |
| --- | --- |
| SSH non entra | IP, utente dell'email OVHcloud, porta 22, chiave corretta e regole firewall. Usa la console KVM se il sistema è accessibile ma SSH no. |
| DNS punta altrove | Modifica la zona DNS dei nameserver realmente usati; attendi la propagazione. |
| HTTPS non funziona | Record A, porte 80/443, `sudo systemctl status caddy` e log Caddy. |
| Job GitHub `deploy` saltato | `DEPLOY_ENABLED` non è `true`, oppure il run è una pull request. |
| Job `deploy` fallisce con SSH | Verifica `DEPLOY_HOST`, chiave privata completa, riga `DEPLOY_KNOWN_HOSTS` e test SSH dell'utente `deploy`. |
| `passhalo` non parte | Leggi `journalctl`; controlla Java 25, file dei secret, password DB e `HIBERNATE_DDL_AUTO`. |
| QR non arriva via email | Controlla SMTP, mittente verificato, spam e log `BookingConfirmationRetryScheduler`. L'invio viene ritentato; gli utenti possono scrivere a `booking@passhalo.it` e il proprietario dell'evento può accodare un reinvio dalla lista prenotazioni. |

## 12. Aggiornamenti, backup e APK

Per gli aggiornamenti ordinari: lavora su un branch, apri una pull request, attendi i tre controlli, fai il merge e controlla il job di deploy e le funzioni principali del sito. Per ripubblicare lo stesso commit puoi usare **Actions → Verify and deploy PassHalo → Run workflow** su `main`.

**Backup:** prima di accettare prenotazioni vere, apri nel pannello OVHcloud **Bare Metal Cloud → Virtual private servers → il tuo VPS → Automated backup** e verifica che sia presente un punto di ripristino. Per i VPS ordinati dal 7 agosto 2025 OVHcloud indica un backup giornaliero incluso, conservato per 24 ore; l'opzione Premium conserva sette backup giornalieri. Verifica l'opzione e il prezzo effettivi sul tuo VPS. [Guida OVHcloud ai backup automatici](https://docs.ovhcloud.com/en/guides/bare-metal-cloud/virtual-private-servers/using-automated-backups-on-a-vps).

Conserva anche una **copia PostgreSQL fuori dal VPS**, almeno prima di ogni modifica dello schema. Per crearla, **VPS / SSH**:

```bash
backup_file="/tmp/passhalo-$(date +%Y%m%d-%H%M%S).dump"
sudo -u postgres pg_dump -Fc -f "$backup_file" passhalo
sudo chown "$USER:$USER" "$backup_file"
chmod 600 "$backup_file"
ls -lh "$backup_file"
```

Annota il nome mostrato da `ls`. **PC / PowerShell**, crea una cartella **fuori dal repository** e copia il dump sostituendo `NOME_FILE.dump` con quel nome:

```powershell
$backupDirectory = Join-Path $env:USERPROFILE 'PassHaloBackups'
New-Item -ItemType Directory -Force $backupDirectory | Out-Null
scp UTENTE_INIZIALE@IP_DEL_VPS:/tmp/NOME_FILE.dump $backupDirectory
```

Se accedi con la chiave personale, aggiungi `-i "$env:USERPROFILE\.ssh\passhalo_admin"` subito dopo `scp`. Controlla che il file locale abbia dimensione maggiore di zero, poi sul VPS elimina **solo** il dump temporaneo appena scaricato con `rm /tmp/NOME_FILE.dump`. Trasferisci il dump locale in un archivio cifrato, rimuovi la copia non cifrata e prova periodicamente il ripristino su un database separato. Conserva separatamente le chiavi PII: un dump senza chiave di cifratura non permette di recuperare i dati personali. Il backup OVHcloud e il dump manuale hanno funzioni diverse; programma una frequenza adeguata quando inizieranno i dati reali.

Prima di ogni modifica dello schema, esegui un backup e prova la migrazione su una copia del database.

Il database nuovo viene creato al primo avvio con `HIBERNATE_DDL_AUTO=update`, poi resta su `validate`. **Non usare `update` per gli aggiornamenti successivi.** Prepara e revisiona una migrazione SQL prima di pubblicare codice che cambia lo schema. I file [`20260927_widen_event_content.sql`](../backend/db/migrations/20260927_widen_event_content.sql), [`20260929_owner_brevo.sql`](../backend/db/migrations/20260929_owner_brevo.sql) e [`20260929_booking_confirmation_retry.sql`](../backend/db/migrations/20260929_booking_confirmation_retry.sql) riguardano database esistenti e non devono essere applicati al nuovo DB vuoto.

Per cambiare password DB/SMTP o JWT, aggiorna `/etc/passhalo/passhalo.properties` sul VPS e riavvia con `sudo systemctl restart passhalo`. Cambiare JWT invalida le sessioni. **Non sostituire le chiavi PII dopo che sono stati salvati dati reali** senza una procedura di ricifratura dei dati e delle chiavi Brevo memorizzate nel database.

### APK Android

La pipeline aggiorna backend e sito, **non genera una nuova APK**. Il profilo `preview` in [`frontend/mobile/eas.json`](../frontend/mobile/eas.json) usa `https://passhalo.it/api`. Dopo aver verificato il sito, apri PowerShell nella cartella `frontend/mobile`:

```powershell
npm ci
npm run typecheck
npx eas-cli@latest login
npx eas-cli@latest build:configure
npm run build:apk
```

Al primo avvio EAS può chiedere di collegare il progetto Expo e creare le credenziali Android. La build restituisce un link per scaricare l'APK. Installala prima sul tuo telefono e prova login e scanner. Per le build successive normalmente basta `npm run build:apk`. Vedi il [README mobile](../frontend/mobile/README.md).

## 13. Collegare Brevo come organizzatore (facoltativo)

1. Nel tuo account Brevo crea una lista dedicata alle campagne dei tuoi eventi e annota l'ID della lista. Le campagne si preparano e si inviano dentro Brevo.
2. In **Settings → SMTP & API → API Keys & MCP** crea una chiave chiamata, per esempio, `PassHalo`. Brevo mostra la chiave una sola volta. Custodiscila come una password. [Guida Brevo alle chiavi API](https://help.brevo.com/hc/en-us/articles/209467485-Create-and-manage-your-API-keys).
3. In PassHalo accedi come proprietario degli eventi e apri **Il tuo account → Il tuo account Brevo** sul web, oppure **Impostazioni → Il tuo account Brevo** nell'APK. Inserisci ID lista e chiave. PassHalo verifica la lista, crea il webhook di disiscrizione, cifra la chiave e accoda i contatti con consenso valido. La chiave non viene più restituita al dispositivo.
4. Controlla nel pannello Brevo che i contatti attesi compaiano nella lista. La sincronizzazione avviene in background circa ogni minuto. Se Brevo non risponde, PassHalo conserva le operazioni in attesa e riprova; il numero in attesa è visibile nella pagina Account.
5. Se la chiave scade o viene revocata, creane una nuova **nello stesso account Brevo** e usa **Sostituisci chiave API** in PassHalo. Per scollegare Brevo, attendi che i contatti in attesa siano zero e usa **Scollega Brevo**.

La revoca da PassHalo o la scadenza del consenso rimuove il contatto dalla lista configurata. La disiscrizione da una campagna Brevo arriva a PassHalo tramite webhook e rimuove il consenso locale per quell'organizzatore. Ogni organizzatore deve usare il proprio account e una lista dedicata.
