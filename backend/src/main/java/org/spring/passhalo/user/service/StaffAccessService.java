package org.spring.passhalo.user.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.booking.dto.CheckInResponse;
import org.spring.passhalo.booking.enums.PaymentMethod;
import org.spring.passhalo.booking.service.BookingService;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.exception.AccessDeniedException;
import org.spring.passhalo.event.exception.EventNotFoundException;
import org.spring.passhalo.event.repository.EventRepository;
import org.spring.passhalo.event.service.WalkInCounterService;
import org.spring.passhalo.user.dto.StaffAccessRequestResponse;
import org.spring.passhalo.user.dto.StaffAccessStatusResponse;
import org.spring.passhalo.user.dto.StaffCodeResponse;
import org.spring.passhalo.user.entity.StaffAccessCode;
import org.spring.passhalo.user.entity.StaffAccessRequest;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.StaffAccessState;
import org.spring.passhalo.user.exception.StaffAccessException;
import org.spring.passhalo.user.repository.StaffAccessCodeRepository;
import org.spring.passhalo.user.repository.StaffAccessRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class StaffAccessService {
    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int CODE_LENGTH = 16;
    private static final int MAX_ATTEMPTS = 30;
    private static final long ATTEMPT_WINDOW_MILLIS = 10 * 60 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${app.time-zone}")
    private String timeZone;

    private final EventRepository eventRepository;
    private final StaffAccessCodeRepository codeRepository;
    private final StaffAccessRequestRepository requestRepository;
    private final BookingService bookingService;
    private final WalkInCounterService walkInCounterService;
    private final EntityManager entityManager;
    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public record BrowserGrant(String secret, LocalDateTime expiresAt, StaffAccessStatusResponse status) {
    }

    private record AttemptWindow(long startedAt, int count) {
    }

    @Transactional
    public StaffCodeResponse generateCode(Long eventId, User owner) {
        Event event = eventRepository.findDistinctById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        requireOwner(event, owner);
        LocalDateTime now = now();
        requireOpen(event, now);
        expireForEvent(eventId, now);

        String code = randomCode();
        StaffAccessCode stored = new StaffAccessCode();
        stored.setEvent(event);
        stored.setCodeHash(hash(code));
        stored.setCreatedAt(now);
        stored.setExpiresAt(now.plusDays(7).isBefore(event.getEndDateTime())
                ? now.plusDays(7) : event.getEndDateTime());
        codeRepository.save(stored);
        return new StaffCodeResponse(formatCode(code), stored.getExpiresAt());
    }

    @Transactional
    public BrowserGrant submitRequest(String suppliedCode, String previousSecret, String remoteAddress) {
        limitAttempts(remoteAddress);
        String code = normalizeCode(suppliedCode);
        StaffAccessCode stored = codeRepository.findByCodeHash(hash(code))
                .orElseThrow(this::invalidCode);
        LocalDateTime now = now();
        Event event = eventRepository.findDistinctById(stored.getEvent().getId())
                .orElseThrow(this::invalidCode);
        entityManager.refresh(stored);
        if (stored.getRevokedAt() != null || !now.isBefore(stored.getExpiresAt()) || !isOpen(event, now)) {
            throw invalidCode();
        }

        if (previousSecret != null && !previousSecret.isBlank()) {
            requestRepository.findBySessionHash(hash(previousSecret)).ifPresent(previous -> {
                if (previous.getState() == StaffAccessState.PENDING || previous.getState() == StaffAccessState.APPROVED) {
                    previous.setState(StaffAccessState.EXPIRED);
                    previous.setDecidedAt(now);
                }
            });
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        StaffAccessRequest request = new StaffAccessRequest();
        request.setEvent(event);
        request.setSessionHash(hash(secret));
        request.setState(StaffAccessState.PENDING);
        request.setCreatedAt(now);
        request.setExpiresAt(event.getEndDateTime());
        requestRepository.save(request);
        return new BrowserGrant(secret, request.getExpiresAt(), toStatus(request, now));
    }

    @Transactional(readOnly = true)
    public StaffAccessStatusResponse status(String secret) {
        StaffAccessRequest request = findSession(secret);
        return toStatus(request, now());
    }

    @Transactional(readOnly = true)
    public List<StaffAccessRequestResponse> pendingRequests(Long eventId, User owner) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        requireOwner(event, owner);
        if (!isOpen(event, now())) return List.of();
        return requestRepository.findAllByEventIdAndState(eventId, StaffAccessState.PENDING).stream()
                .filter(request -> now().isBefore(request.getExpiresAt()))
                .map(request -> new StaffAccessRequestResponse(request.getId(), request.getState(), request.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void decide(Long eventId, Long requestId, User owner, boolean approve) {
        Event event = eventRepository.findDistinctById(eventId)
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        requireOwner(event, owner);
        LocalDateTime now = now();
        requireOpen(event, now);
        StaffAccessRequest request = requestRepository.findByIdAndEventId(requestId, eventId)
                .orElseThrow(() -> new StaffAccessException("Richiesta non trovata", HttpStatus.NOT_FOUND));
        if (request.getState() != StaffAccessState.PENDING || !now.isBefore(request.getExpiresAt())) {
            throw new StaffAccessException("Richiesta già conclusa", HttpStatus.CONFLICT);
        }
        request.setState(approve ? StaffAccessState.APPROVED : StaffAccessState.REJECTED);
        request.setDecidedAt(now);
    }

    @Transactional
    public CheckInResponse checkIn(String secret, UUID uuid, PaymentMethod paymentMethod) {
        Event event = approvedEvent(secret);
        return bookingService.checkInBookingForEvent(uuid, event.getId(), paymentMethod);
    }

    @Transactional
    public void addWalkIn(String secret, PaymentMethod paymentMethod) {
        Event event = approvedEvent(secret);
        walkInCounterService.adjust(event, paymentMethod, true);
    }

    @Transactional
    public void removeWalkIn(String secret, PaymentMethod paymentMethod) {
        Event event = approvedEvent(secret);
        walkInCounterService.adjust(event, paymentMethod, false);
    }

    @Transactional
    public void logout(String secret) {
        if (secret == null || secret.isBlank() || secret.length() > 128) return;
        requestRepository.findBySessionHash(hash(secret)).ifPresent(request -> {
            request.setState(StaffAccessState.EXPIRED);
            request.setDecidedAt(now());
        });
    }

    @Transactional
    public void expireForEvent(Long eventId) {
        expireForEvent(eventId, now());
    }

    @Transactional
    public void deleteForEvent(Long eventId) {
        requestRepository.deleteAll(requestRepository.findAllByEventId(eventId));
        codeRepository.deleteAll(codeRepository.findAllByEventId(eventId));
        requestRepository.flush();
        codeRepository.flush();
    }

    private void expireForEvent(Long eventId, LocalDateTime now) {
        for (StaffAccessCode code : codeRepository.findAllByEventIdAndRevokedAtIsNull(eventId)) {
            code.setRevokedAt(now);
        }
        for (StaffAccessRequest request : requestRepository.findAllByEventIdAndStateIn(
                eventId, List.of(StaffAccessState.PENDING, StaffAccessState.APPROVED))) {
            request.setState(StaffAccessState.EXPIRED);
            request.setDecidedAt(now);
        }
    }

    private Event approvedEvent(String secret) {
        StaffAccessRequest request = findSession(secret);
        Event event = eventRepository.findDistinctById(request.getEvent().getId())
                .orElseThrow(() -> new EventNotFoundException("Event not found"));
        entityManager.refresh(request);
        if (request.getState() != StaffAccessState.APPROVED ||
                !now().isBefore(request.getExpiresAt()) || !isOpen(event, now())) {
            throw new AccessDeniedException("Accesso staff scaduto o non approvato");
        }
        return event;
    }

    private StaffAccessRequest findSession(String secret) {
        if (secret == null || secret.isBlank() || secret.length() > 128) {
            throw new StaffAccessException("Sessione staff assente", HttpStatus.UNAUTHORIZED);
        }
        return requestRepository.findBySessionHash(hash(secret))
                .orElseThrow(() -> new StaffAccessException("Sessione staff assente", HttpStatus.UNAUTHORIZED));
    }

    private StaffAccessStatusResponse toStatus(StaffAccessRequest request, LocalDateTime now) {
        Event event = request.getEvent();
        StaffAccessState state = request.getState();
        if (state != StaffAccessState.REJECTED &&
                (!now.isBefore(request.getExpiresAt()) || !isOpen(event, now))) {
            state = StaffAccessState.EXPIRED;
        }
        return new StaffAccessStatusResponse(state, event.getId(), event.getName(), request.getExpiresAt());
    }

    private void requireOwner(Event event, User owner) {
        if (owner == null || !event.getUser().getId().equals(owner.getId())) {
            throw new AccessDeniedException("Solo il proprietario può gestire lo staff");
        }
    }

    private void requireOpen(Event event, LocalDateTime now) {
        if (!isOpen(event, now)) throw new StaffAccessException("Evento terminato", HttpStatus.CONFLICT);
    }

    private boolean isOpen(Event event, LocalDateTime now) {
        return event.getEventState() != EventState.FINISHED && now.isBefore(event.getEndDateTime());
    }

    private String normalizeCode(String supplied) {
        if (supplied == null || supplied.length() > 64) throw invalidCode();
        String code = supplied.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != CODE_LENGTH) throw invalidCode();
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) throw invalidCode();
        }
        return code;
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return code.toString();
    }

    private String formatCode(String code) {
        return code.substring(0, 4) + "-" + code.substring(4, 8) + "-" +
                code.substring(8, 12) + "-" + code.substring(12);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private void limitAttempts(String remoteAddress) {
        String key = remoteAddress == null ? "unknown" : remoteAddress;
        long now = System.currentTimeMillis();
        AttemptWindow window = attempts.compute(key, (ignored, current) ->
                current == null || now - current.startedAt() >= ATTEMPT_WINDOW_MILLIS
                        ? new AttemptWindow(now, 1) : new AttemptWindow(current.startedAt(), current.count() + 1));
        if (window.count() > MAX_ATTEMPTS) {
            throw new StaffAccessException("Troppi tentativi. Riprova più tardi", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (attempts.size() > 10_000) {
            attempts.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= ATTEMPT_WINDOW_MILLIS);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of(timeZone));
    }

    private StaffAccessException invalidCode() {
        return new StaffAccessException("Codice non valido o scaduto", HttpStatus.BAD_REQUEST);
    }
}
