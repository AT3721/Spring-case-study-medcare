package com.codegym.service.auth;

public interface IEmailService {
    void sendPasswordResetEmail(String toEmail, String recipientName, String resetLink);
    void sendAppointmentConfirmationEmail(com.codegym.model.Appointment appointment);
}
