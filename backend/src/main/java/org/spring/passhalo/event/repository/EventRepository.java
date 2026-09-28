package org.spring.passhalo.event.repository;

import jakarta.persistence.LockModeType;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Event> findDistinctById(Long id);
    List<Event> findByUserId(Long userId);

    @Query("select e.id from Event e where e.eventState <> :finished and e.endDateTime <= :now")
    List<Long> findEndedEventIds(EventState finished, LocalDateTime now);
}
