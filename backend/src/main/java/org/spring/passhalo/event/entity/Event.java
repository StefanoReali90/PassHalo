package org.spring.passhalo.event.entity;

import jakarta.persistence.*;
import lombok.*;
import org.spring.passhalo.event.enums.EventState;
import org.spring.passhalo.user.entity.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@NoArgsConstructor
@Setter
@Getter
@EqualsAndHashCode
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDateTime startDateTime;

    @Column(nullable = false)
    private LocalDateTime endDateTime;

    @Column(nullable = true)
    private Double normalPrice;

    @Column(nullable = false)
    private Double bookingPrice;

    @Column(nullable = false)
    private int totalTickets;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventState eventState;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false, length = 2048)
    private String imageUrl;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private int walkInCount = 0;

    @Column(length = 2048)
    private String videoUrl;

    @ElementCollection
    @CollectionTable(name = "event_faqs", joinColumns = @JoinColumn(name = "event_id"))
    @OrderColumn(name = "faq_order")
    private List<EventFaq> faqs = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;



}
