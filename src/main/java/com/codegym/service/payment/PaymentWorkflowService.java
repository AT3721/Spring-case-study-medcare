package com.codegym.service.payment;

import com.codegym.dto.payment.VNPayCallbackResult;
import com.codegym.model.Appointment;
import com.codegym.model.AppointmentStatus;
import com.codegym.model.PaymentStatus;
import com.codegym.model.User;
import com.codegym.repository.IAppointmentRepository;
import com.codegym.service.auth.AuthenticatedUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentWorkflowService {
    private final IVNPayService vnPayService;
    private final IAppointmentRepository appointmentRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public PaymentWorkflowService(IVNPayService vnPayService,
                                  IAppointmentRepository appointmentRepository,
                                  AuthenticatedUserService authenticatedUserService) {
        this.vnPayService = vnPayService;
        this.appointmentRepository = appointmentRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public String createPaymentUrl(String bookingCode, Authentication authentication,
                                   HttpServletRequest request) {
        Appointment appointment = appointmentRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn: " + bookingCode));

        if (authentication != null && authentication.isAuthenticated()) {
            User currentUser = authenticatedUserService.findCurrentUser(authentication);
            if (currentUser != null && appointment.getPatientAccount() != null
                    && !appointment.getPatientAccount().getId().equals(currentUser.getId())) {
                throw new IllegalArgumentException("Bạn không có quyền thanh toán lịch hẹn này.");
            }
        }
        if (appointment.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalArgumentException("Lịch hẹn này đã được thanh toán trước đó.");
        }
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new IllegalArgumentException("Lịch hẹn đã bị hủy, không thể thực hiện thanh toán.");
        }
        return vnPayService.createPaymentUrl(appointment, request);
    }

    public VNPayCallbackResult processReturn(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return vnPayService.processReturn(params);
    }
}
