package org.spring.passhalo.notification.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.spring.passhalo.user.entity.EventInvitation;
import org.spring.passhalo.user.enums.EventRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${MAIL_FROM}")
    private String from;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private final JavaMailSender mailSender;

    @Async
    public void sendBookingConfirmation(String to, String customerName, String eventName, byte[] qrCodeBytes, String unsubscribeToken) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject("Conferma prenotazione per " + eventName);
            String body = "Gentile " + customerName + ",\n\nLa tua prenotazione per l'evento " + eventName + " è stata confermata.\n\nAllegato il codice QR per il tuo ingresso.";
            if (unsubscribeToken != null) {
                body += "\n\nHai acconsentito a ricevere comunicazioni promozionali. Puoi revocare il consenso e cancellare i dati marketing in qualsiasi momento qui: "
                        + frontendBaseUrl.replaceAll("/$", "") + "/marketing/unsubscribe#token=" + unsubscribeToken;
            }
            helper.setText(body);
            helper.addAttachment("passhalo_ticket.png", new ByteArrayResource(qrCodeBytes));
            mailSender.send(mimeMessage);


            log.info("Booking confirmation email sent");
        } catch (MessagingException e) {
            log.error("Failed to send booking confirmation email - Error type: {}", e.getClass().getSimpleName());
            throw new RuntimeException(e);
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
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");

            helper.setFrom(from);
            helper.setTo(eventInvitation.getRecipientEmail());
            helper.setSubject("Invito a collaborare all’evento: " + eventName);
            helper.setText("Sei stato invitato a collaborare all’evento " + eventName +" come "+ role + ".\n  Accetta l’invito entro " + expiresAt+".\n Per poter accettare accedi all'app e inserisci il codice: " + token);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("Failed to send event invitation email - Error type: {}", e.getClass().getSimpleName());
            throw new RuntimeException(e);

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
            log.info("Password reset email sent");
        } catch (MessagingException exception) {
            log.error("Failed to send password reset email - Error type: {}", exception.getClass().getSimpleName());
            throw new IllegalStateException("Unable to send password reset email", exception);
        }
    }
}
