package org.spring.passhalo.notification.service;

import lombok.RequiredArgsConstructor;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.spring.passhalo.notification.entity.OwnerSmtpSettings;
import org.spring.passhalo.notification.repository.OwnerSmtpSettingsRepository;
import org.spring.passhalo.security.PiiCryptoService;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class OwnerSmtpSettingsService {
    private static final Pattern HOST = Pattern.compile("(?i)^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@<>]+@[^\\s@<>]+\\.[^\\s@<>]+$");

    private final OwnerSmtpSettingsRepository repository;
    private final UserRepository userRepository;
    private final PiiCryptoService cryptoService;
    private final SmtpHostValidator hostValidator;

    @Transactional(readOnly = true)
    public Status status(String username) {
        User owner = owner(username);
        return repository.findByOwnerId(owner.getId()).map(this::status)
                .orElse(new Status(false, null, null, null, null, null, null));
    }

    @Transactional
    public Status save(String username, SettingsRequest request) {
        User owner = owner(username);
        String host = required(request.host(), 253, "Host SMTP").toLowerCase(Locale.ROOT);
        if (!HOST.matcher(host).matches()) badRequest("Host SMTP non valido");
        if (request.port() != 465 && request.port() != 587 && request.port() != 2525) {
            badRequest("Usa una porta SMTP di invio: 465, 587 o 2525");
        }
        if (request.encryption() == null) badRequest("Scegli STARTTLS oppure SSL");
        String smtpUsername = required(request.username(), 320, "Utente SMTP");
        String fromEmail = required(request.fromEmail(), 320, "Indirizzo mittente").toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(fromEmail).matches()) badRequest("Indirizzo mittente non valido");
        String fromName = required(request.fromName(), 100, "Nome mittente");
        if (fromName.contains("\r") || fromName.contains("\n") || smtpUsername.contains("\r") || smtpUsername.contains("\n")) {
            badRequest("Caratteri non validi nelle impostazioni SMTP");
        }
        hostValidator.validate(host);
        OwnerSmtpSettings settings = repository.findByOwnerId(owner.getId()).orElseGet(() -> {
            OwnerSmtpSettings created = new OwnerSmtpSettings();
            created.setOwner(owner);
            return created;
        });
        if (request.password() != null && !request.password().isBlank()) {
            String password = request.password();
            if (password.length() > 512 || password.contains("\r") || password.contains("\n")) badRequest("Password SMTP non valida");
            settings.setPasswordCiphertext(cryptoService.encrypt(password));
        } else if (settings.getPasswordCiphertext() == null) {
            badRequest("Password SMTP obbligatoria");
        }
        settings.setHost(host);
        settings.setPort(request.port());
        settings.setEncryption(request.encryption());
        settings.setUsername(smtpUsername);
        settings.setFromEmail(fromEmail);
        settings.setFromName(fromName);
        return status(repository.save(settings));
    }

    @Transactional
    public void delete(String username) {
        User owner = owner(username);
        repository.findByOwnerId(owner.getId()).ifPresent(repository::delete);
    }

    @Transactional(readOnly = true)
    public Optional<MailRoute> route(Long ownerId) {
        return repository.findByOwnerId(ownerId).map(settings -> {
            hostValidator.validate(settings.getHost());
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setHost(settings.getHost());
            sender.setPort(settings.getPort());
            sender.setUsername(settings.getUsername());
            sender.setPassword(cryptoService.decrypt(settings.getPasswordCiphertext()));
            sender.setDefaultEncoding("UTF-8");
            Properties properties = sender.getJavaMailProperties();
            properties.setProperty("mail.smtp.auth", "true");
            properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(settings.getEncryption() == OwnerSmtpSettings.Encryption.STARTTLS));
            properties.setProperty("mail.smtp.starttls.required", Boolean.toString(settings.getEncryption() == OwnerSmtpSettings.Encryption.STARTTLS));
            properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(settings.getEncryption() == OwnerSmtpSettings.Encryption.SSL));
            properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
            properties.setProperty("mail.smtp.connectiontimeout", "10000");
            properties.setProperty("mail.smtp.timeout", "10000");
            properties.setProperty("mail.smtp.writetimeout", "10000");
            return new MailRoute(sender, settings.getFromEmail(), settings.getFromName());
        });
    }

    public Long ownerId(String username) {
        return owner(username).getId();
    }

    public void sendTest(String username) {
        User owner = owner(username);
        MailRoute route = route(owner.getId()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.CONFLICT, "Configura prima il server SMTP"));
        try {
            MimeMessage message = route.sender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(route.fromEmail(), route.fromName());
            helper.setTo(owner.getEmail());
            helper.setSubject("Verifica mittente PassHalo");
            helper.setText("Questa email conferma che il server SMTP configurato per i tuoi eventi funziona.");
            route.sender().send(message);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Invio di prova non riuscito. Controlla credenziali, cifratura e mittente verificato.");
        }
    }

    private User owner(String username) {
        User user = userRepository.findByEmailIgnoreCase(username).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getRole() != Role.ADMIN) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return user;
    }

    private Status status(OwnerSmtpSettings settings) {
        return new Status(true, settings.getHost(), settings.getPort(), settings.getEncryption(),
                settings.getUsername(), settings.getFromEmail(), settings.getFromName());
    }

    private static String required(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) badRequest(field + " non valido");
        return value.trim();
    }

    private static void badRequest(String message) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record SettingsRequest(String host, int port, OwnerSmtpSettings.Encryption encryption,
                                  String username, String password, String fromEmail, String fromName) { }
    public record Status(boolean configured, String host, Integer port, OwnerSmtpSettings.Encryption encryption,
                         String username, String fromEmail, String fromName) { }
    public record MailRoute(JavaMailSender sender, String fromEmail, String fromName) { }
}
