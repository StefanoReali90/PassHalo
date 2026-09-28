package org.spring.passhalo.booking.entity;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.spring.passhalo.booking.enums.BookingStatus;
import org.spring.passhalo.event.entity.Event;
import org.spring.passhalo.event.enums.EventState;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(indexes = @Index(name = "ix_booking_event_email_lookup_hash", columnList = "event_id,email_lookup_hash"))
@NoArgsConstructor
@Setter
@Getter
@EqualsAndHashCode
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Legacy plaintext column retained only while existing rows are migrated.
    @Column(nullable = true)
    private String name;

    // Legacy plaintext column retained only while existing rows are migrated.
    @Column(nullable = true)
    private String surname;

    // Legacy plaintext column retained only while existing rows are migrated.
    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    private String phone;

    @Column(name = "name_ciphertext", length = 1024)
    private String nameCiphertext;

    @Column(name = "surname_ciphertext", length = 1024)
    private String surnameCiphertext;

    @Column(name = "email_ciphertext", length = 1024)
    private String emailCiphertext;

    @Column(name = "phone_ciphertext", length = 1024)
    private String phoneCiphertext;

    @Column(name = "email_lookup_hash", length = 67)
    private String emailLookupHash;

    @Column(nullable = false, unique = true)
    private UUID uuid;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BookingStatus bookingStatus = BookingStatus.CREATED;

    @Column(nullable = true)
    private LocalDateTime checkInDateTime;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean marketingConsent = false;

    @Column(nullable = true)
    private Timestamp consentAt;

    @PrePersist
    public void prePersist() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;


}
