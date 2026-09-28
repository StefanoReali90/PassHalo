package org.spring.passhalo.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.event.repository.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventLifecycleScheduler {
    private final EventRepository eventRepository;
    private final EventService eventService;

    @Value("${app.time-zone}")
    private String timeZone;

    @Scheduled(cron = "0 * * * * *", zone = "${app.time-zone}")
    public void closeEndedEvents() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        for (Long eventId : eventRepository.findEndedEventIds(EventState.FINISHED, now)) {
            try {
                eventService.closeExpiredEvent(eventId);
            } catch (RuntimeException exception) {
                log.error("Unable to close ended event {} - Error type: {}", eventId,
                        exception.getClass().getSimpleName());
            }
        }
    }
}
