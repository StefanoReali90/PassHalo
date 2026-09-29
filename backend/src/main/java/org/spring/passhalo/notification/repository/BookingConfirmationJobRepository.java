package org.spring.passhalo.notification.repository;

import org.spring.passhalo.notification.entity.BookingConfirmationJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingConfirmationJobRepository extends JpaRepository<BookingConfirmationJob, Long> {
    Optional<BookingConfirmationJob> findByBookingId(Long bookingId);

    @Query("select j.id from BookingConfirmationJob j where j.nextAttemptAt <= :now " +
            "order by j.nextAttemptAt, j.id")
    List<Long> findReadyIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("update BookingConfirmationJob j set j.nextAttemptAt = :leaseUntil " +
            "where j.id = :id and j.nextAttemptAt <= :now")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now,
              @Param("leaseUntil") LocalDateTime leaseUntil);

    void deleteByBookingId(Long bookingId);

    void deleteByBooking_Event_Id(Long eventId);
}
