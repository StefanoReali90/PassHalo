# Conteggio persone per metodo di pagamento

Funzionalita implementata il 4 ottobre 2026 nel backend, nel web e nell'app mobile.
L'utente ha autorizzato anche la modifica diretta del backend.

## Comportamento

- Ogni nuovo ingresso richiede Contanti (`CASH`) oppure Carta / POS (`CARD`).
- Si contano persone, non transazioni: un pagamento per quattro persone richiede
  quattro registrazioni di ingresso con quel metodo.
- La prenotazione nasce senza metodo; questo viene salvato insieme al check-in.
- Nel web e nel mobile la scelta si azzera dopo ogni registrazione riuscita.
- Gli ingressi senza prenotazione aggiornano il totale e il contatore del metodo.
- Le correzioni rimuovono una persona soltanto dal metodo selezionato. Se il relativo
  conteggio e zero, il server risponde 409 senza modificare gli altri conteggi.
- Il pagamento non richiede integrazione con il terminale POS.
- Non viene salvato un importo per persona: gli incassi esistenti continuano a usare
  `bookingPrice` e `normalPrice` dell'evento.

## Persistenza e consistenza

`Booking.paymentMethod` e un enum persistito come stringa, nullable per le
prenotazioni non ancora convalidate e per i vecchi ingressi non classificati.

`Event.walkInCashCount` e `Event.walkInCardCount` partono da zero;
`Event.walkInCount` conserva il totale esistente. Gli ingressi non classificati sono
la differenza tra il totale e i due contatori. Non vengono attribuiti ai contanti.

`WalkInCounterService` condivide la logica tra account autenticati e sessioni staff.
Entrambi i percorsi autorizzano l'evento e lo caricano con lock di scrittura nella
transazione. Il check-in conserva il lock sulla prenotazione: una seconda scansione
non cambia il metodo gia registrato.

La dashboard usa una lettura REPEATABLE_READ per ottenere conteggi coerenti anche
mentre vengono registrati nuovi ingressi. I metodi restano dopo anonimizzazione.
Lo STAFF registra il metodo ma non riceve statistiche o dati personali.

## Contratto API

Le richieste seguenti devono includere un corpo JSON con `paymentMethod`:

- PATCH `/bookings/check-in/{uuid}`.
- PATCH `/bookings/events/{eventId}/check-in/{uuid}`.
- PATCH `/events/{id}/walk-in`.
- PATCH `/events/{id}/walk-in/decrement`.
- POST `/staff-access/walk-ins`.
- POST `/staff-access/walk-ins/decrement`.

POST `/staff-access/check-in` richiede sia `uuid` sia `paymentMethod`.
Un metodo mancante, null o diverso da CASH/CARD produce 400.

GET `/events/{id}/dashboard` aggiunge `checkedInCashCount`, `checkedInCardCount`,
`checkedInUnrecordedCount`, `walkInCashCount`, `walkInCardCount` e
`walkInUnrecordedCount`. Web e mobile mostrano la suddivisione per tipo di ingresso
oltre ai totali per metodo. I dati storici compaiono come "Metodo non registrato".

## Aggiornamento di un database esistente

Prima di avviare il nuovo backend eseguire
`backend/db/migrations/20261004_payment_method_counts.sql` sul database PostgreSQL.
La migrazione aggiunge tre colonne e vincoli sui metodi e sui contatori; non assegna
un metodo agli ingressi storici.

Migrazione applicata dall'utente e verificata sul database PostgreSQL locale
`passhalo`: presenti tutte e tre le colonne, entrambi i vincoli risultano validati
e non sono presenti contatori di pagamento incoerenti. Verifica effettuata in una
transazione di sola lettura. Gli altri ambienti richiedono la propria migrazione.

Aggiornare insieme backend, web e app mobile: i vecchi client che non inviano
`paymentMethod` ricevono 400. L'avvio ordinario valida lo schema senza modificarlo.

## Verifiche

I test di integrazione coprono conteggi per persona e metodo, dati storici,
prenotazioni non presentate/annullate, metodi mancanti/non validi, correzioni,
chiusura e anonimizzazione. Sono aggiornati anche i test di accesso staff e
isolamento tra eventi. I test concorrenti verificano che una doppia scansione
non sostituisca il primo metodo e che gli ingressi simultanei non perdano conteggi.
