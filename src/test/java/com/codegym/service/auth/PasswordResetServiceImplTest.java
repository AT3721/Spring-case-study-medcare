package com.codegym.service.auth;

import com.codegym.model.PasswordResetToken;
import com.codegym.model.User;
import com.codegym.repository.IPasswordResetTokenRepository;
import com.codegym.repository.IUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    @Mock
    private IUserRepository userRepository;

    @Mock
    private IPasswordResetTokenRepository tokenRepository;

    @Mock
    private IEmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("patient@example.com", "oldEncodedPassword", "Nguyen Van A");
        user.setId(5L);
        user.setEmail("patient@example.com");
    }

    @Test
    void processForgotPasswordGeneratesTokenWith15MinuteExpiryAndSendsEmail() {
        when(userRepository.findByEmailIgnoreCase("patient@example.com")).thenReturn(Optional.of(user));

        String token = passwordResetService.processForgotPassword("patient@example.com", "http://localhost:8080");

        assertNotNull(token);
        assertFalse(token.isBlank());

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, times(1)).save(tokenCaptor.capture());

        PasswordResetToken savedToken = tokenCaptor.getValue();
        assertEquals(token, savedToken.getToken());
        assertEquals(user, savedToken.getUser());
        assertFalse(savedToken.isUsed());
        assertFalse(savedToken.isExpired());
        assertTrue(savedToken.getExpiryDate().isAfter(LocalDateTime.now().plusMinutes(14)));
        assertTrue(savedToken.getExpiryDate().isBefore(LocalDateTime.now().plusMinutes(16)));

        verify(emailService, times(1)).sendPasswordResetEmail(
                eq("patient@example.com"), eq("Nguyen Van A"), contains(token));
    }

    @Test
    void processForgotPasswordReturnsNullIfUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("unknown@example.com")).thenReturn(Optional.empty());

        String token = passwordResetService.processForgotPassword("unknown@example.com", "http://localhost:8080");

        assertNull(token);
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    @Test
    void validateTokenReturnsTrueForFreshUnusedToken() {
        PasswordResetToken resetToken = new PasswordResetToken("valid-token", user, 15);
        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(resetToken));

        assertTrue(passwordResetService.validateToken("valid-token"));
    }

    @Test
    void validateTokenReturnsFalseForExpiredToken() {
        PasswordResetToken resetToken = new PasswordResetToken("expired-token", user, 15);
        resetToken.setExpiryDate(LocalDateTime.now().minusMinutes(1)); // expired
        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(resetToken));

        assertFalse(passwordResetService.validateToken("expired-token"));
    }

    @Test
    void validateTokenReturnsFalseForUsedToken() {
        PasswordResetToken resetToken = new PasswordResetToken("used-token", user, 15);
        resetToken.setUsed(true);
        when(tokenRepository.findByToken("used-token")).thenReturn(Optional.of(resetToken));

        assertFalse(passwordResetService.validateToken("used-token"));
    }

    @Test
    void resetPasswordEncodesNewPasswordAndMarksTokenAsUsed() {
        PasswordResetToken resetToken = new PasswordResetToken("valid-token", user, 15);
        when(tokenRepository.findByToken("valid-token")).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("newPassword123")).thenReturn("bcryptHash123");

        boolean result = passwordResetService.resetPassword("valid-token", "newPassword123");

        assertTrue(result);
        assertEquals("bcryptHash123", user.getPassword());
        assertTrue(resetToken.isUsed());

        verify(userRepository, times(1)).save(user);
        verify(tokenRepository, times(1)).save(resetToken);
    }
}
