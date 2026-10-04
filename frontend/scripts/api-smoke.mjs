import assert from 'node:assert/strict';

const baseUrl = (process.env.PASSHALO_API_BASE_URL || 'http://127.0.0.1:8081').replace(/\/$/, '');
const target = new URL(baseUrl);

if (!['127.0.0.1', 'localhost', '::1'].includes(target.hostname)) {
    throw new Error('Lo smoke test può essere eseguito soltanto contro un backend locale isolato.');
}

if (process.env.PASSHALO_ALLOW_SMOKE_WRITE !== 'true') {
    throw new Error('Imposta PASSHALO_ALLOW_SMOKE_WRITE=true per autorizzare la creazione dei dati temporanei.');
}

async function request(path, { method = 'GET', body, cookie, expected = [200], headers = {} } = {}) {
    const response = await fetch(`${baseUrl}${path}`, {
        method,
        headers: {
            ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
            ...(cookie ? { Cookie: cookie } : {}),
            ...headers,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    const raw = await response.text();
    let data = null;
    if (raw) {
        try {
            data = JSON.parse(raw);
        } catch {
            data = raw;
        }
    }

    if (!expected.includes(response.status)) {
        const detail = typeof data === 'object' && data?.detail ? `: ${data.detail}` : '';
        throw new Error(`${method} ${path} ha risposto ${response.status}${detail}`);
    }

    return { data, headers: response.headers, status: response.status };
}

function jwtCookie(headers) {
    const raw = headers.get('set-cookie') || '';
    const match = raw.match(/(?:^|,\s*)(jwt=[^;]*)/);
    assert.ok(match, 'Cookie JWT non ricevuto dal login');
    return match[1];
}

function pass(label) {
    console.log(`✓ ${label}`);
}

const stamp = Date.now();
const adminEmail = `admin-${stamp}@passhalo.test`;
const staffEmail = `staff-${stamp}@passhalo.test`;
const guestEmails = Array.from({ length: 4 }, (_, index) => `guest-${index + 1}-${stamp}@passhalo.test`);
const initialPassword = 'PassHalo!123';
const updatedPassword = 'PassHalo!456';
const start = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000);
const end = new Date(start.getTime() + 6 * 60 * 60 * 1000);
const localDateTime = (value) => value.toISOString().slice(0, 19);

await request('/events');
await request('/events/1/dashboard', { expected: [403] });
const preflight = await request('/bookings/', {
    method: 'OPTIONS',
    expected: [200],
    headers: {
        Origin: 'http://127.0.0.1:5173',
        'Access-Control-Request-Method': 'POST',
    },
});
assert.equal(preflight.headers.get('access-control-allow-origin'), 'http://127.0.0.1:5173');
assert.equal(preflight.headers.get('access-control-allow-credentials'), 'true');
pass('accesso pubblico, protezione dashboard e CORS');

await request('/user/register', {
    method: 'POST',
    expected: [201],
    body: {
        name: 'Admin',
        surname: 'Smoke',
        email: adminEmail,
        password: initialPassword,
    },
});
const adminLogin = await request('/user/login', {
    method: 'POST',
    body: { email: adminEmail, password: initialPassword },
});
let adminCookie = jwtCookie(adminLogin.headers);
const currentAdmin = await request('/user/me', { cookie: adminCookie });
assert.equal(currentAdmin.data.role, 'ADMIN');
pass('registrazione, login e sessione ADMIN');

await request('/user/staff-register', {
    method: 'POST',
    cookie: adminCookie,
    expected: [201],
    body: { name: 'Staff', surname: 'Smoke', email: staffEmail, password: initialPassword },
});
const staffSearch = await request(`/user/search?email=${encodeURIComponent(staffEmail)}`, { cookie: adminCookie });
assert.equal(staffSearch.data.role, 'STAFF');
const staffById = await request('/user/2', { cookie: adminCookie });
assert.equal(staffById.data.email, staffEmail);
const users = await request('/user', { cookie: adminCookie });
assert.equal(users.data.length, 2);

const staffLogin = await request('/user/login', {
    method: 'POST',
    body: { email: staffEmail, password: initialPassword },
});
const staffCookie = jwtCookie(staffLogin.headers);
await request('/user', { cookie: staffCookie, expected: [403] });
pass('creazione STAFF e autorizzazioni per ruolo');

const eventPayload = {
    name: 'PassHalo E2E',
    description: 'Evento temporaneo per il collaudo frontend',
    location: 'Test Arena',
    start: localDateTime(start),
    end: localDateTime(end),
    imageUrl: 'https://example.com/passhalo-e2e.jpg',
    videoUrl: 'https://www.youtube.com/watch?v=aqz-KE-bpKQ',
    faqs: [
        {
            question: 'Come ricevo il pass?',
            answer: 'Il QR code viene mostrato al termine della prenotazione.',
        },
        {
            question: 'Posso usare il pass due volte?',
            answer: 'No, ogni QR code può essere convalidato una sola volta.',
        },
    ],
    totalTickets: 100,
    normalPrice: 15,
    bookingPrice: 10,
};
const createdEvent = await request('/events/', {
    method: 'POST',
    cookie: adminCookie,
    expected: [201],
    body: eventPayload,
});
const eventId = createdEvent.data.id;
assert.ok(eventId > 0);
assert.equal(createdEvent.data.videoUrl, eventPayload.videoUrl);
assert.deepEqual(createdEvent.data.faqs, eventPayload.faqs);

const updatedEventPayload = {
    ...eventPayload,
    name: 'PassHalo E2E aggiornato',
    videoUrl: 'https://vimeo.com/76979871',
    faqs: [
        {
            question: 'Quando devo mostrare il QR?',
            answer: 'Mostralo allo staff al momento dell’ingresso.',
        },
    ],
};
const updatedEvent = await request(`/events/${eventId}`, {
    method: 'PUT',
    cookie: adminCookie,
    body: updatedEventPayload,
});
assert.equal(updatedEvent.data.name, 'PassHalo E2E aggiornato');
assert.equal(updatedEvent.data.videoUrl, updatedEventPayload.videoUrl);
assert.deepEqual(updatedEvent.data.faqs, updatedEventPayload.faqs);
const publicEvent = await request(`/events/${eventId}`);
assert.equal(publicEvent.data.id, eventId);
assert.equal(publicEvent.data.videoUrl, updatedEventPayload.videoUrl);
assert.deepEqual(publicEvent.data.faqs, updatedEventPayload.faqs);
const ownedEvents = await request('/events/my-events', { cookie: adminCookie });
assert.equal(ownedEvents.data.length, 1);
assert.equal(ownedEvents.data[0].videoUrl, updatedEventPayload.videoUrl);
assert.deepEqual(ownedEvents.data[0].faqs, updatedEventPayload.faqs);

const disposableEvent = await request('/events/', {
    method: 'POST',
    cookie: adminCookie,
    expected: [201],
    body: { ...eventPayload, name: 'Evento eliminabile' },
});
await request(`/events/${disposableEvent.data.id}`, { method: 'DELETE', cookie: adminCookie, expected: [204] });
pass('CRUD eventi e lista eventi amministratore');

const bookings = [];
for (let index = 0; index < guestEmails.length; index += 1) {
    const created = await request('/bookings/', {
        method: 'POST',
        expected: [201],
        body: {
            name: `Guest${index + 1}`,
            surname: 'Smoke',
            email: guestEmails[index],
            phone: index === 0 ? '+39 333 0000000' : '',
            eventId,
            marketingConsent: index === 0,
        },
    });
    bookings.push(created.data);
}

const byUuid = await request(`/bookings/${bookings[0].uuid}`, { cookie: adminCookie });
assert.equal(byUuid.data.email, guestEmails[0]);
const byId = await request('/bookings/bookingId/2', { cookie: adminCookie });
assert.equal(byId.data.uuid, bookings[1].uuid);
const byEvent = await request(`/bookings/events/${eventId}`, { cookie: adminCookie });
assert.equal(byEvent.data.length, 4);
const byEmail = await request(`/bookings/email/${encodeURIComponent(guestEmails[0])}`, { cookie: adminCookie });
assert.equal(byEmail.data.length, 1);
const byEventAndEmail = await request(`/bookings/event/${eventId}/email/${encodeURIComponent(guestEmails[0])}`, { cookie: adminCookie });
assert.equal(byEventAndEmail.data.length, 1);
const allBookings = await request('/bookings/', { cookie: adminCookie });
assert.equal(allBookings.data.length, 4);

await request('/bookings/2', { method: 'DELETE', cookie: adminCookie, expected: [204] });
const cancelled = await request('/bookings/bookingId/2', { cookie: adminCookie });
assert.equal(cancelled.data.bookingStatus, 'CANCELLED');
pass('creazione, ricerca, elenco e annullamento prenotazioni');

const adminCheckIn = await request(`/bookings/check-in/${bookings[0].uuid}`, { method: 'PATCH', body: { paymentMethod: 'CASH' }, cookie: adminCookie });
assert.equal(adminCheckIn.data.eventName, 'PassHalo E2E aggiornato');
const staffCheckIn = await request(`/bookings/check-in/${bookings[2].uuid}`, { method: 'PATCH', body: { paymentMethod: 'CASH' }, cookie: staffCookie });
assert.equal(staffCheckIn.data.name, 'Guest3');
await request(`/bookings/check-in/${bookings[0].uuid}`, { method: 'PATCH', body: { paymentMethod: 'CASH' }, cookie: staffCookie, expected: [409] });
await request(`/bookings/check-in/${bookings[1].uuid}`, { method: 'PATCH', body: { paymentMethod: 'CASH' }, cookie: staffCookie, expected: [409] });
await request(`/events/${eventId}/dashboard`, { cookie: staffCookie, expected: [403] });
pass('check-in ADMIN/STAFF, duplicato, annullato e segregazione statistiche');

await request(`/events/${eventId}/walk-in`, { method: 'PATCH', body: { paymentMethod: 'CASH' }, cookie: adminCookie, expected: [204] });
await request(`/events/${eventId}/walk-in`, { method: 'PATCH', body: { paymentMethod: 'CASH' }, cookie: adminCookie, expected: [204] });
await request(`/events/${eventId}/walk-in/decrement`, { method: 'PATCH', body: { paymentMethod: 'CASH' }, cookie: adminCookie, expected: [204] });
const dashboard = await request(`/events/${eventId}/dashboard`, { cookie: adminCookie });
assert.equal(dashboard.data.totalBookings, 3);
assert.equal(dashboard.data.checkedInCount, 2);
assert.equal(dashboard.data.noShowCount, 1);
assert.equal(dashboard.data.walkInCount, 1);
assert.equal(dashboard.data.totalAttendees, 3);
assert.equal(dashboard.data.totalRevenue, 35);
assert.ok(Math.abs(dashboard.data.attendanceRate - (200 / 3)) < 0.01);
pass('contatori, ricavi e valori reali della dashboard');

await request(`/events/${eventId}/close`, { method: 'PATCH', cookie: adminCookie, expected: [204] });
const finishedEvent = await request(`/events/${eventId}`);
assert.equal(finishedEvent.data.eventState, 'FINISHED');
const anonymizedBooking = await request(`/bookings/${bookings[0].uuid}`, { cookie: adminCookie });
assert.equal(anonymizedBooking.data.name, 'ANONYMIZED');
assert.match(anonymizedBooking.data.email, /^anon_.+@anonymized\.local$/);
await request(`/bookings/check-in/${bookings[3].uuid}`, { method: 'PATCH', body: { paymentMethod: 'CASH' }, cookie: staffCookie, expected: [409] });
pass('chiusura evento, anonimizzazione e blocco check-in post evento');

await request('/user/recover-password', { method: 'POST', expected: [204], body: { email: adminEmail } });
await request('/user/reset-password', {
    method: 'PATCH',
    expected: [401],
    body: { token: 'token-non-valido', newPassword: updatedPassword, confirmationPassword: updatedPassword },
});
await request('/user/1/change-password', {
    method: 'PATCH',
    cookie: adminCookie,
    expected: [204],
    body: { oldPassword: initialPassword, newPassword: updatedPassword, confirmationPassword: updatedPassword },
});
const relogin = await request('/user/login', {
    method: 'POST',
    body: { email: adminEmail, password: updatedPassword },
});
adminCookie = jwtCookie(relogin.headers);
await request('/user/2', { method: 'DELETE', cookie: adminCookie, expected: [204] });
await request('/user/logout', { method: 'POST', cookie: adminCookie });
pass('recupero password, cambio password, eliminazione STAFF e logout');

console.log('\nSmoke test completato: contratto API e risultati dashboard verificati.');
