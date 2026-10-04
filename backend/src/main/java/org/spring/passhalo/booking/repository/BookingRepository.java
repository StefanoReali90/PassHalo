package org.spring.passhalo.booking.repository;

import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.spring.passhalo.booking.entity.Booking;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.booking.enums.PaymentMethod;
import org.spring.passhalo.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByUuid(UUID uuid);
    List<Booking> findAllByEvent_User_Id(Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.uuid = :uuid")
    Optional<Booking> findForCheckInByUuid(@Param("uuid") UUID uuid);

    boolean existsByEventIdAndEmailLookupHashAndBookingStatusNot(Long eventId, String emailLookupHash, BookingStatus bookingStatus);
    boolean existsByEventIdAndEmailIgnoreCaseAndBookingStatusNot(Long eventId, String email, BookingStatus bookingStatus);

    long countByEventId(Long eventId);
    long countByEventIdAndBookingStatusNot(Long eventId, BookingStatus bookingStatus);
    List<Booking> findAllByEventId(Long eventId);
    List<Booking> findAllByEventIdAndEmailIgnoreCase(Long eventId, String email);
    List<Booking> findAllByEmailIgnoreCaseAndEvent_User_Id(String email, Long userId);
    List<Booking> findAllByEmailLookupHash(String emailLookupHash);
    List<Booking> findAllByEventIdAndEmailLookupHash(Long eventId, String emailLookupHash);
    List<Booking> findAllByEmailLookupHashAndEvent_User_Id(String emailLookupHash, Long userId);
    List<Booking> findTop500ByEmailLookupHashIsNullAndEmailIsNotNullOrderByIdAsc();

    long countByEventIdAndBookingStatus(Long eventId, BookingStatus bookingStatus);

    long countByEventIdAndBookingStatusAndPaymentMethod(Long eventId, BookingStatus bookingStatus, PaymentMethod paymentMethod);
}
