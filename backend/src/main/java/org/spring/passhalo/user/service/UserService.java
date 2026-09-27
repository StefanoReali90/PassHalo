package org.spring.passhalo.user.service;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final UserMapper userMapper;
    private final AuthenticationManager authenticationManager;

    private final JwtService jwtService;

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

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream().map(userMapper::toResponse).toList();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
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
            UUID resetToken = UUID.randomUUID();
            user.setResetPasswordToken(resetToken.toString());
            user.setResetPasswordTokenExpiry(LocalDateTime.now().plusMinutes(15));
            userRepository.save(user);

        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetPasswordToken(request.token()).orElseThrow(() -> new TokenExpiredException("Invalid reset token"));

        if (user.getResetPasswordTokenExpiry() == null || user.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now())) {
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

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
        userRepository.delete(user);
    }

    public LoginResponse login(@Valid LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getEmail(), request.password()));
        String token = jwtService.generateToken(user);
        return new LoginResponse(token);
    }
}
