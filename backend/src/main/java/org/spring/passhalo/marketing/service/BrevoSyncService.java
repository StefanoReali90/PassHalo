package org.spring.passhalo.marketing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.marketing.entity.BrevoConnection;
import org.spring.passhalo.marketing.entity.BrevoSyncJob;
import org.spring.passhalo.marketing.entity.MarketingSubscriber;
import org.spring.passhalo.marketing.repository.BrevoConnectionRepository;
import org.spring.passhalo.marketing.repository.BrevoSyncJobRepository;
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
public class BrevoSyncService {
    private final BrevoSyncJobRepository jobRepository;
    private final BrevoConnectionRepository connectionRepository;
    private final MarketingRepository marketingRepository;
    private final PiiCryptoService cryptoService;
    private final BrevoApiClient apiClient;
    private final TransactionTemplate transactions;

    @Transactional
    public void queue(User owner, String emailLookupHash, String emailCiphertext) {
        if (!connectionRepository.findByOwnerId(owner.getId()).isPresent()) return;
        BrevoSyncJob job = jobRepository.findByOwnerIdAndEmailLookupHash(owner.getId(), emailLookupHash)
                .orElseGet(BrevoSyncJob::new);
        job.setOwner(owner);
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
                .stream().map(BrevoSyncJob::getId).toList());
        if (ids == null) return;
        for (Long id : ids) {
            try {
                Pending pending = transactions.execute(status -> snapshot(id));
                if (pending == null) continue;
                if (pending.subscriber() == null) {
                    apiClient.removeFromList(pending.apiKey(), pending.listId(), pending.email());
                } else {
                    MarketingSubscriber subscriber = pending.subscriber();
                    apiClient.upsertContact(pending.apiKey(), pending.listId(), pending.email(),
                            cryptoService.decrypt(subscriber.getNameCiphertext()),
                            cryptoService.decrypt(subscriber.getSurnameCiphertext()));
                }
                transactions.executeWithoutResult(status -> jobRepository.findById(id).ifPresent(job -> {
                    if (job.getRevision() == pending.revision()) jobRepository.delete(job);
                }));
            } catch (RuntimeException exception) {
                // No contact, key, or response body is included in logs.
                log.warn("Sincronizzazione Brevo fallita jobId={} errore={}", id, exception.getClass().getSimpleName());
                transactions.executeWithoutResult(status -> jobRepository.findById(id).ifPresent(job -> {
                    job.setUpdatedAt(LocalDateTime.now());
                    jobRepository.save(job);
                }));
            }
        }
    }

    private Pending snapshot(Long id) {
        BrevoSyncJob job = jobRepository.findById(id).orElse(null);
        if (job == null) return null;
        BrevoConnection connection = connectionRepository.findByOwnerId(job.getOwner().getId()).orElse(null);
        if (connection == null) return null;
        MarketingSubscriber subscriber = marketingRepository
                .findAllByOwnerIdAndEmailLookupHash(job.getOwner().getId(), job.getEmailLookupHash())
                .stream().filter(row -> row.isActive() && row.getExpiresAt() != null
                        && row.getExpiresAt().isAfter(LocalDateTime.now())).findFirst().orElse(null);
        return new Pending(job.getRevision(), cryptoService.decrypt(connection.getApiKeyCiphertext()),
                connection.getListId(), cryptoService.decrypt(job.getEmailCiphertext()), subscriber);
    }

    private record Pending(long revision, String apiKey, long listId, String email, MarketingSubscriber subscriber) { }
}
