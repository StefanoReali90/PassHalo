package org.spring.passhalo.notification.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.event.repository.EventRepository;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceRoutingTest {
    @Mock private JavaMailSender platformSender;
    @Mock private JavaMailSender organizerSender;
    @Mock private OwnerSmtpSettingsService settings;
    @Mock private EventRepository events;
    private EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(platformSender, settings, events);
        ReflectionTestUtils.setField(service, "from", "platform@example.com");
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "https://passhalo.it");
    }

    @Test
    void bookingConfirmationUsesTheEventOwnersSender() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(events.findOwnerIdByEventId(42L)).thenReturn(Optional.of(7L));
        when(settings.route(7L)).thenReturn(Optional.of(new OwnerSmtpSettingsService.MailRoute(
                organizerSender, "booking@organizer.example", "Organizzatore")));
        when(organizerSender.createMimeMessage()).thenReturn(message);

        service.sendBookingConfirmation(42L, "guest@example.com", "Ada", "Evento", new byte[]{1, 2}, null);

        assertEquals("booking@organizer.example", ((InternetAddress) message.getFrom()[0]).getAddress());
        verify(organizerSender).send(message);
        verifyNoInteractions(platformSender);
    }

    @Test
    void passwordResetAlwaysUsesThePlatformSender() {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(platformSender.createMimeMessage()).thenReturn(message);

        service.sendPasswordReset("owner@example.com", "token");

        verify(platformSender).send(message);
        verifyNoInteractions(organizerSender, settings, events);
    }
}
