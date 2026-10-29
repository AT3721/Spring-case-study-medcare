package com.codegym.service.auth;

import com.codegym.model.PasswordResetToken;
import com.codegym.model.User;
import com.codegym.repository.IPasswordResetTokenRepository;
import com.codegym.repository.IUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetServiceImpl implements IPasswordResetService {

    public static final int EXPIRY_MINUTES = 15;

    private final IUserRepository userRepository;
    private final IPasswordResetTokenRepository tokenRepository;
    private final IEmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetServiceImpl(IUserRepository userRepository,
                                    IPasswordResetTokenRepository tokenRepository,
                                    IEmailService emailService,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public String processForgotPassword(String emailOrUsername, String baseUrl) {
        if (emailOrUsername == null || emailOrUsername.isBlank()) {
            return null;
        }

        String query = emailOrUsername.trim().toLowerCase();
        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(query)
                .or(() -> userRepository.findByUsername(query));

        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();

        String rawToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        PasswordResetToken resetToken = new PasswordResetToken(rawToken, user, EXPIRY_MINUTES);
        tokenRepository.save(resetToken);

        String resetLink = baseUrl + "/reset-password?token=" + rawToken;
        String recipientEmail = user.getEmail() != null && !user.getEmail().isBlank()
                ? user.getEmail()
                : (user.getUsername().contains("@") ? user.getUsername() : user.getUsername() + "@medcare.vn");

        emailService.sendPasswordResetEmail(recipientEmail, user.getFullName(), resetLink);

        return rawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return tokenRepository.findByToken(token)
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank()) {
            return false;
        }

        Optional<PasswordResetToken> tokenOpt = tokenRepository.findByToken(token);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken resetToken = tokenOpt.get();
        if (!resetToken.isValid()) {
            return false;
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        return true;
    }
}
