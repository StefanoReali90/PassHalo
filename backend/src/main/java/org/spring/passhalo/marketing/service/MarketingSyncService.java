package org.spring.passhalo.marketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.marketing.entity.MarketingConnection;
import org.spring.passhalo.marketing.entity.MarketingSyncJob;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.spring.passhalo.marketing.repository.MarketingConnectionRepository;
import org.spring.passhalo.marketing.repository.MarketingSyncJobRepository;
import org.spring.passhalo.marketing.repository.MarketingRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.entity.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketingSyncService {
    private final MarketingSyncJobRepository jobRepository;
    private final MarketingConnectionRepository connectionRepository;
    private final MarketingRepository marketingRepository;
    private final PiiCryptoService cryptoService;
    private final List<MarketingProviderAdapter> adapters;
    private final TransactionTemplate transactions;

    @Transactional
    public void queue(User owner, String emailLookupHash, String emailCiphertext) {
        MarketingConnection connection = connectionRepository.findByOwnerId(owner.getId()).orElse(null);
        if (connection == null) return;
        MarketingSyncJob job = jobRepository.findByConnectionIdAndEmailLookupHash(connection.getId(), emailLookupHash)
                .orElseGet(MarketingSyncJob::new);
        job.setConnection(connection);
        job.setEmailLookupHash(emailLookupHash);
        job.setEmailCiphertext(emailCiphertext);
        job.setUpdatedAt(LocalDateTime.now());
        job.setRevision(job.getRevision() + 1);
        jobRepository.save(job);
    }

    @Transactional
    public void queueAllActive(User owner) {
        marketingRepository.findAllByOwnerIdAndIsActiveTrueAndExpiresAtAfter(owner.getId(), LocalDateTime.now())
                .forEach(subscriber -> queue(owner, subscriber.getEmailLookupHash(), subscriber.getEmailCiphertext()));
    }

    @Scheduled(fixedDelay = 60_000)
    public void flushPending() {
        List<Long> ids = transactions.execute(status -> jobRepository.findReady(PageRequest.of(0, 20))
                .stream().map(MarketingSyncJob::getId).toList());
        if (ids == null) return;
        for (Long id : ids) {
            try {
                Pending pending = transactions.execute(status -> snapshot(id));
                if (pending == null) continue;
                MarketingProviderAdapter adapter = adapters.stream()
                        .filter(candidate -> candidate.provider().equals(pending.connection().getProvider()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Marketing provider not supported"));
                if (pending.subscriber() == null) {
                    adapter.removeContact(pending.connection(), pending.email());
                } else {
                    MarketingSubscriber subscriber = pending.subscriber();
                    adapter.upsertContact(pending.connection(), pending.email(),
                            cryptoService.decrypt(subscriber.getNameCiphertext()),
                            cryptoService.decrypt(subscriber.getSurnameCiphertext()));
                }
                transactions.executeWithoutResult(status -> jobRepository.findById(id).ifPresent(job -> {
                    if (job.getRevision() == pending.revision()) jobRepository.delete(job);
                }));
            } catch (RuntimeException exception) {
                // No contact, key, or response body is included in logs.
                log.warn("Sincronizzazione marketing fallita jobId={} errore={}", id, exception.getClass().getSimpleName());
                transactions.executeWithoutResult(status -> jobRepository.findById(id).ifPresent(job -> {
                    job.setUpdatedAt(LocalDateTime.now());
                    jobRepository.save(job);
                }));
            }
        }
    }

    private Pending snapshot(Long id) {
        MarketingSyncJob job = jobRepository.findById(id).orElse(null);
        if (job == null) return null;
        MarketingConnection connection = job.getConnection();
        // Initialize the detached snapshot before leaving the transaction.
        connection.getProvider();
        MarketingSubscriber subscriber = marketingRepository
                .findAllByOwnerIdAndEmailLookupHash(connection.getOwner().getId(), job.getEmailLookupHash())
                .stream().filter(row -> row.isActive() && row.getExpiresAt() != null
                        && row.getExpiresAt().isAfter(LocalDateTime.now())).findFirst().orElse(null);
        return new Pending(job.getRevision(), connection, cryptoService.decrypt(job.getEmailCiphertext()), subscriber);
    }

    private record Pending(long revision, MarketingConnection connection, String email, MarketingSubscriber subscriber) { }
}
