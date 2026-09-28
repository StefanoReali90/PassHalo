package org.spring.passhalo.user.service;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.spring.passhalo.notification.service.EmailService;
import org.spring.passhalo.user.dto.*;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.exception.*;
import org.spring.passhalo.user.mapper.UserMapper;
import org.spring.passhalo.user.repository.UserRepository;
import org.spring.passhalo.user.security.JwtService;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final SecureRandom RESET_RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;
    private final EmailService emailService;

    @Transactional
    public UserResponse createUser(AdminRegistrationRequest request) {
        String cleanedEmail = request.email() != null ? request.email().trim().toLowerCase(Locale.ROOT) : null;

        if (cleanedEmail != null && userRepository.existsByEmailIgnoreCase(cleanedEmail)) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        User user = new User();
        user.setName(request.name());
        user.setSurname(request.surname());
        user.setEmail(cleanedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.ADMIN);
        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);


    }

    @Transactional
    public UserResponse createStaffUser(StaffRegistrationRequest request) {
        String cleanedEmail = request.email() != null ? request.email().trim().toLowerCase(Locale.ROOT) : null;
        if (cleanedEmail != null && userRepository.existsByEmailIgnoreCase(cleanedEmail)) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        User user = new User();
        user.setName(request.name());
        user.setSurname(request.surname());
        user.setEmail(cleanedEmail);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.STAFF);
        User savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, String authenticatedEmail) {
        User user = userRepository.findByEmailIgnoreCase(authenticatedEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        if (!request.newPassword().equals(request.confirmationPassword())) {
            throw new InvalidPasswordException("New password and confirmation password do not match");
        }
        if (request.newPassword().length() < 8) {
            throw new InvalidPasswordException("New password must be at least 8 characters long");
        }
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void recoverPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase(Locale.ROOT)).ifPresent(user -> {
            byte[] randomBytes = new byte[32];
            RESET_RANDOM.nextBytes(randomBytes);
            String resetToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
            user.setResetPasswordToken(hashResetToken(resetToken));
            user.setResetPasswordTokenExpiry(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);
            emailService.sendPasswordReset(user.getEmail(), resetToken);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (request.token() == null || request.token().isBlank() || request.token().length() > 128) {
            throw new TokenExpiredException("Invalid reset token");
        }
        User user = userRepository.findByResetPasswordToken(hashResetToken(request.token().trim()))
                .orElseThrow(() -> new TokenExpiredException("Invalid reset token"));

        if (user.getResetPasswordTokenExpiry() == null || !user.getResetPasswordTokenExpiry().isAfter(LocalDateTime.now())) {
            throw new TokenExpiredException("Reset token has expired");
        }
        if (request.newPassword().length() < 8) {
            throw new InvalidPasswordException("New password must be at least 8 characters long");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new InvalidPasswordException("New password can't be the same as the old password");
        }
        if (!request.newPassword().equals(request.confirmationPassword())) {
            throw new InvalidPasswordException("New password and confirmation password do not match");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);
    }

    private String hashResetToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public LoginResponse login(@Valid LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(), request.password()));
        String token = jwtService.generateToken(user);
        return new LoginResponse(token);
    }
}
