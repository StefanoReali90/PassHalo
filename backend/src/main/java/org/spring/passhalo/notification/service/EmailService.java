package org.spring.passhalo.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.user.entity.EventInvitation;
import org.spring.passhalo.user.enums.EventRole;
import org.spring.passhalo.event.repository.EventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${MAIL_FROM}")
    private String from;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private final JavaMailSender mailSender;
    private final OwnerSmtpSettingsService smtpSettings;
    private final EventRepository eventRepository;

    public void sendBookingConfirmation(Long eventId, String to, String customerName, String eventName,
                                        byte[] qrCodeBytes, String unsubscribeToken) {
        try {
            OwnerSmtpSettingsService.MailRoute route = routeForEvent(eventId);
            MimeMessage mimeMessage = route.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(route.fromEmail(), route.fromName());
            helper.setTo(to);
            helper.setSubject("Conferma prenotazione per " + eventName);
            String body = "Gentile " + customerName + ",\n\nLa tua prenotazione per l'evento " + eventName + " è stata confermata.\n\nAllegato il codice QR per il tuo ingresso.";
            if (unsubscribeToken != null) {
                body += "\n\nHai acconsentito a ricevere comunicazioni promozionali. Puoi revocare il consenso e cancellare i dati marketing in qualsiasi momento qui: "
                        + frontendBaseUrl.replaceAll("/$", "") + "/marketing/unsubscribe#token=" + unsubscribeToken;
            }
            helper.setText(body);
            helper.addAttachment("passhalo_ticket.png", new ByteArrayResource(qrCodeBytes));
            route.sender().send(mimeMessage);


        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            log.error("Invio email conferma prenotazione fallito eventoId={} errore={}",
                    eventId, e.getClass().getSimpleName());
            throw new IllegalStateException("Invio email conferma prenotazione fallito");
        }
    }

    public void sendEmailConfirmation(EventInvitation eventInvitation, String token) {
        String eventName = eventInvitation.getEvent().getName();
        String role;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String expiresAt = eventInvitation.getExpiresAt().format(dtf);
        if (eventInvitation.getProposedRole() == EventRole.EVENT_ADMIN){
            role ="Amministratore";

        }else{
            role="Staff";
        }
        try {
            OwnerSmtpSettingsService.MailRoute route = routeForEvent(eventInvitation.getEvent().getId());
            MimeMessage mimeMessage = route.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(route.fromEmail(), route.fromName());
            helper.setTo(eventInvitation.getRecipientEmail());
            helper.setSubject("Invito a collaborare all’evento: " + eventName);
            helper.setText("Sei stato invitato a collaborare all’evento " + eventName +" come "+ role + ".\n  Accetta l’invito entro " + expiresAt+".\n Per poter accettare accedi all'app e inserisci il codice: " + token);
            route.sender().send(mimeMessage);
            log.info("Email invito collaborazione inviata eventoId={}", eventInvitation.getEvent().getId());
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException e) {
            log.error("Invio email invito collaborazione fallito eventoId={} errore={}",
                    eventInvitation.getEvent().getId(), e.getClass().getSimpleName());
            throw new IllegalStateException("Invio email invito collaborazione fallito");

        }


    }

    public void sendPasswordReset(String to, String token) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Reimposta la password PassHalo");
            helper.setText("Hai richiesto una nuova password. Apri questo link entro 15 minuti: "
                    + frontendBaseUrl.replaceAll("/$", "") + "/reset-password#token=" + token
                    + "\n\nSe non hai fatto questa richiesta, ignora questa email.");
            mailSender.send(message);
            log.info("Email ripristino password inviata");
        } catch (MessagingException | RuntimeException exception) {
            log.error("Invio email ripristino password fallito errore={}", exception.getClass().getSimpleName());
            throw new IllegalStateException("Invio email ripristino password fallito");
        }
    }

    private OwnerSmtpSettingsService.MailRoute routeForEvent(Long eventId) {
        Long ownerId = eventRepository.findOwnerIdByEventId(eventId)
                .orElseThrow(() -> new IllegalStateException("Evento email non trovato"));
        return smtpSettings.route(ownerId)
                .orElseGet(() -> new OwnerSmtpSettingsService.MailRoute(mailSender, from, "PassHalo"));
    }
}
