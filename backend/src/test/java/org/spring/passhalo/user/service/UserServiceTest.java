package org.spring.passhalo.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.spring.passhalo.user.dto.AdminRegistrationRequest;
import org.spring.passhalo.user.dto.LoginRequest;
import org.spring.passhalo.user.dto.LoginResponse;
import org.spring.passhalo.user.dto.ForgotPasswordRequest;
import org.spring.passhalo.user.dto.ResetPasswordRequest;
import org.spring.passhalo.user.dto.StaffRegistrationRequest;
import org.spring.passhalo.user.dto.UserResponse;
import org.spring.passhalo.user.entity.User;
import org.spring.passhalo.user.enums.Role;
import org.spring.passhalo.user.exception.EmailAlreadyExistsException;
import org.spring.passhalo.user.mapper.UserMapper;
import org.spring.passhalo.user.repository.UserRepository;
import org.spring.passhalo.user.security.JwtService;
import org.spring.passhalo.notification.service.EmailService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private EmailService emailService;
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, passwordEncoder, new UserMapper(),
                authenticationManager, jwtService, emailService);
    }

    @Test
    void staffSignupNormalizesEmailAndCannotChooseAdminRole() {
        when(passwordEncoder.encode("strong-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = service.createStaffUser(new StaffRegistrationRequest(
                "Ada", "Rossi", "ADA@Example.Test", "strong-password"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).existsByEmailIgnoreCase("ada@example.test");
        verify(userRepository).save(saved.capture());
        assertEquals("ada@example.test", saved.getValue().getEmail());
        assertEquals("encoded-password", saved.getValue().getPassword());
        assertEquals(Role.STAFF, saved.getValue().getRole());
        assertEquals("STAFF", response.role());
    }

    @Test
    void duplicateEmailCheckIgnoresCaseForAdminSignup() {
        when(userRepository.existsByEmailIgnoreCase("admin@example.test")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> service.createUser(
                new AdminRegistrationRequest("Test", "Owner", "ADMIN@Example.Test", "strong-password")));

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void loginAuthenticatesUsingStoredEmailForLegacyMixedCaseAccounts() {
        User legacy = new User();
        legacy.setEmail("Legacy@Example.Test");
        legacy.setRole(Role.STAFF);
        when(userRepository.findByEmailIgnoreCase("legacy@example.test")).thenReturn(Optional.of(legacy));
        when(jwtService.generateToken(legacy)).thenReturn("signed-jwt");

        LoginResponse response = service.login(new LoginRequest("LEGACY@EXAMPLE.TEST", "secret"));

        ArgumentCaptor<Authentication> authentication = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authentication.capture());
        assertEquals("Legacy@Example.Test", authentication.getValue().getPrincipal());
        assertEquals("secret", authentication.getValue().getCredentials());
        assertEquals("signed-jwt", response.token());
    }

    @Test
    void recoveryEmailsRawTokenButStoresOnlyHashAndConsumesItOnce() {
        User user = new User();
        user.setEmail("guest@example.test");
        user.setPassword("old-hash");
        when(userRepository.findByEmailIgnoreCase("guest@example.test")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        service.recoverPassword(new ForgotPasswordRequest(" GUEST@example.test "));

        ArgumentCaptor<String> sentToken = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordReset(org.mockito.ArgumentMatchers.eq("guest@example.test"), sentToken.capture());
        assertNotEquals(sentToken.getValue(), user.getResetPasswordToken());
        assertEquals(64, user.getResetPasswordToken().length());

        String storedHash = user.getResetPasswordToken();
        when(userRepository.findByResetPasswordToken(storedHash)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        service.resetPassword(new ResetPasswordRequest(sentToken.getValue(), "new-password", "new-password"));

        verify(userRepository).findByResetPasswordToken(storedHash);
        assertEquals("new-hash", user.getPassword());
        assertNull(user.getResetPasswordToken());
        assertNull(user.getResetPasswordTokenExpiry());
    }

    @Test
    void recoveryOfUnknownEmailDoesNotSendAnEmail() {
        service.recoverPassword(new ForgotPasswordRequest("unknown@example.test"));
        verifyNoInteractions(emailService);
    }

    @Test
    void unknownLoginEmailReturnsGenericCredentialsError() {
        assertThrows(BadCredentialsException.class,
                () -> service.login(new LoginRequest("unknown@example.test", "wrong-password")));
        verifyNoInteractions(authenticationManager, jwtService);
    }
}
