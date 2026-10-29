package com.codegym.service.auth;

public interface IPasswordResetService {
    String processForgotPassword(String emailOrUsername, String baseUrl);
    boolean validateToken(String token);
    boolean resetPassword(String token, String newPassword);
}
