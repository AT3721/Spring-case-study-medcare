package com.codegym.service.auth;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class EmailServiceImpl implements IEmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:medcare.noreply@gmail.com}")
    private String fromEmail;

    public EmailServiceImpl(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetLink) {
        String displayName = (recipientName != null && !recipientName.isBlank()) ? recipientName : "Quý khách";
        String subject = "MedCare - Yêu cầu đặt lại mật khẩu (Hiệu lực 15 phút)";

        String htmlContent = """
                <div style="font-family: 'Segoe UI', Tahoma, Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 12px; background: #ffffff;">
                    <div style="text-align: center; margin-bottom: 24px;">
                        <h2 style="color: #087e8b; margin: 0;">MedCare Healthcare</h2>
                        <p style="color: #64748b; font-size: 14px; margin-top: 4px;">Hệ thống đặt lịch & quản lý khám bệnh</p>
                    </div>
                    <p style="font-size: 15px; color: #1e293b;">Xin chào <strong>%s</strong>,</p>
                    <p style="font-size: 14px; color: #334155; line-height: 1.6;">
                        Hệ thống MedCare nhận được yêu cầu đặt lại mật khẩu cho tài khoản liên kết với địa chỉ email <strong>%s</strong>.
                    </p>
                    <div style="text-align: center; margin: 28px 0;">
                        <a href="%s" style="display: inline-block; padding: 13px 28px; background: #087e8b; color: #ffffff; text-decoration: none; border-radius: 8px; font-weight: bold; font-size: 15px; box-shadow: 0 4px 10px rgba(8, 126, 139, 0.3);">
                            Đặt Lại Mật Khẩu
                        </a>
                    </div>
                    <p style="font-size: 13px; color: #b45309; background: #fefce8; border: 1px solid #fef08a; padding: 12px; border-radius: 6px;">
                        ⏱️ <strong>Lưu ý quan trọng:</strong> Đường link này chỉ có hiệu lực trong vòng <strong>15 phút</strong>. Sau thời gian này, bạn sẽ cần gửi lại yêu cầu mới.
                    </p>
                    <p style="font-size: 13px; color: #64748b; line-height: 1.5;">
                        Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email. Tài khoản của bạn vẫn an toàn.<br>
                        Nếu không bấm được vào nút trên, bạn có thể sao chép và dán liên kết sau vào trình duyệt:<br>
                        <a href="%s" style="color: #087e8b; word-break: break-all;">%s</a>
                    </p>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;">
                    <p style="font-size: 12px; color: #94a3b8; text-align: center; margin: 0;">
                        MedCare Clinic System &copy; 2026. Đây là email tự động, vui lòng không phản hồi.
                    </p>
                </div>
                """.formatted(displayName, toEmail, resetLink, resetLink, resetLink);

        log.info("==========================================================================");
        log.info("📩 [PASSWORD RESET EMAIL] To: {} | Link: {}", toEmail, resetLink);
        log.info("⏱️  [TOKEN EXPIRATION] Link is valid for 15 minutes");
        log.info("==========================================================================");

        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(fromEmail, "MedCare Clinic");
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("✅ Gửi email đặt lại mật khẩu thành công tới: {}", toEmail);
            } catch (Exception e) {
                log.warn("⚠️ Không thể gửi email qua SMTP (có thể do chưa cấu hình SMTP thật hoặc mạng): {}. Người dùng vẫn có thể dùng link được ghi log ở trên.", e.getMessage());
            }
        }
    }

    @Override
    public void sendAppointmentConfirmationEmail(com.codegym.model.Appointment appointment) {
        String toEmail = appointment.getPatientEmail();
        if (toEmail == null || toEmail.isBlank()) {
            if (appointment.getPatientAccount() != null && appointment.getPatientAccount().getEmail() != null) {
                toEmail = appointment.getPatientAccount().getEmail();
            }
        }
        if (toEmail == null || toEmail.isBlank()) {
            log.info("ℹ️ Lịch hẹn {} không có email để gửi xác nhận.", appointment.getBookingCode());
            return;
        }

        String recipientName = appointment.getPatientName() != null ? appointment.getPatientName() : "Quý khách";
        String doctorName = appointment.getDoctor() != null ? appointment.getDoctor().getName() : "Bác sĩ phòng khám";
        String specialtyName = (appointment.getDoctor() != null && appointment.getDoctor().getSpecialty() != null)
                ? appointment.getDoctor().getSpecialty().getName() : "Đa khoa";
        String dateStr = appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().toString() : "";
        String slotStr = appointment.getTimeSlot() != null ? appointment.getTimeSlot().getDisplayText() : "";
        String bookingCode = appointment.getBookingCode();

        // Generate QR code base64 image
        String qrBase64 = generateQrBase64("MEDCARE-APPOINTMENT:" + bookingCode);

        String subject = "MedCare - Xác nhận thanh toán & Phiếu khám bệnh [" + bookingCode + "]";
        String htmlContent = """
                <div style="font-family: 'Segoe UI', Tahoma, Arial, sans-serif; max-width: 620px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 12px; background: #ffffff;">
                    <div style="text-align: center; margin-bottom: 20px;">
                        <h2 style="color: #087e8b; margin: 0;">MedCare Healthcare</h2>
                        <p style="color: #64748b; font-size: 14px; margin-top: 4px;">Phiếu Xác Nhận Đặt Lịch & Thanh Toán Thành Công</p>
                    </div>
                    <p style="font-size: 15px; color: #1e293b;">Xin chào <strong>%s</strong>,</p>
                    <p style="font-size: 14px; color: #334155; line-height: 1.6;">
                        Lịch hẹn khám bệnh của bạn đã được thanh toán thành công và xác nhận trên hệ thống phòng khám MedCare.
                    </p>
                    <div style="background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 18px; margin: 20px 0;">
                        <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                            <span style="color: #64748b;">Mã đặt lịch:</span>
                            <span style="font-weight: bold; color: #087e8b; font-size: 16px;">%s</span>
                        </div>
                        <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                            <span style="color: #64748b;">Bác sĩ phụ trách:</span>
                            <span style="font-weight: bold; color: #1e293b;">%s (%s)</span>
                        </div>
                        <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                            <span style="color: #64748b;">Ngày khám:</span>
                            <span style="font-weight: bold; color: #1e293b;">%s</span>
                        </div>
                        <div style="display: flex; justify-content: space-between; margin-bottom: 8px;">
                            <span style="color: #64748b;">Khung giờ:</span>
                            <span style="font-weight: bold; color: #1e293b;">%s</span>
                        </div>
                        <div style="display: flex; justify-content: space-between;">
                            <span style="color: #64748b;">Trạng thái thanh toán:</span>
                            <span style="font-weight: bold; color: #166534;">ĐÃ THANH TOÁN (ONLINE)</span>
                        </div>
                    </div>
                    <div style="text-align: center; margin: 24px 0;">
                        <p style="font-size: 13px; font-weight: bold; color: #475569; margin-bottom: 8px;">MÃ QR CHECK-IN TẠI PHÒNG KHÁM:</p>
                        %s
                        <p style="font-size: 12px; color: #64748b; margin-top: 6px;">Quý khách vui lòng xuất trình mã QR này hoặc mã <strong>%s</strong> tại quầy tiếp đón để làm thủ tục vào khám nhanh nhất.</p>
                    </div>
                    <hr style="border: none; border-top: 1px solid #e2e8f0; margin: 24px 0;">
                    <p style="font-size: 12px; color: #94a3b8; text-align: center; margin: 0;">
                        MedCare Clinic System &copy; 2026. Chúc bạn và gia đình luôn dồi dào sức khỏe!
                    </p>
                </div>
                """.formatted(
                recipientName,
                bookingCode,
                doctorName,
                specialtyName,
                dateStr,
                slotStr,
                qrBase64 != null ? "<img src=\"data:image/png;base64," + qrBase64 + "\" alt=\"Mã QR đặt lịch\" style=\"width:180px;height:180px;border:1px solid #cbd5e1;padding:6px;border-radius:8px;\" />" : "",
                bookingCode
        );

        log.info("==========================================================================");
        log.info("📩 [CONFIRMATION EMAIL SENT] To: {} | Booking: {} | Doctor: {} | Date: {}", toEmail, bookingCode, doctorName, dateStr);
        log.info("==========================================================================");

        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                helper.setFrom(fromEmail, "MedCare Clinic");
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("✅ Gửi email xác nhận đặt lịch & QR thành công tới: {}", toEmail);
            } catch (Exception e) {
                log.warn("⚠️ Không thể gửi email qua SMTP: {}. Người dùng vẫn có thể xem mã phiếu trên website.", e.getMessage());
            }
        }
    }

    private String generateQrBase64(String content) {
        try {
            com.google.zxing.qrcode.QRCodeWriter qrCodeWriter = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = qrCodeWriter.encode(
                    content, com.google.zxing.BarcodeFormat.QR_CODE, 200, 200);
            java.io.ByteArrayOutputStream pngOutputStream = new java.io.ByteArrayOutputStream();
            com.google.zxing.client.j2se.MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            return java.util.Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            log.warn("Lỗi sinh QR code: {}", e.getMessage());
            return null;
        }
    }
}
